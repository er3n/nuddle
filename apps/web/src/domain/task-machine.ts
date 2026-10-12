// Task state machine (docs/domain.md, Task states). Every transition is a pure function returning
// the new task, the events to append, the new plan version, the server timers and the push hints.
// The caller persists all of it in one transaction (ADR 0006, 0007).
import { planPokes } from "./plan-pokes";
import type { Reachability } from "./reachability";
import { DAY, HOUR, MINUTE, addMs } from "./time";
import type {
  CancelReason,
  DeviceLevel,
  Edge,
  FinishMode,
  Person,
  PersonId,
  PokePlan,
  PushMessage,
  ScheduledAction,
  Task,
  TaskEvent,
  TaskEventType,
  TaskId,
  TaskStatus,
} from "./types";

export const AUTO_APPROVE_AFTER_MS = 3 * HOUR;
export const MISSED_CARD_TTL_MS = 1 * DAY;
/** (proposed) A step with no fired report this long after its planned time counts as lost. */
const LOST_POKE_GRACE_MS = 5 * MINUTE;
/**
 * Snooze lengths in order; their count is the limit, the last one is "son hakkın".
 * TODO(open-questions#1): 2 snoozes, 15 then 10 minutes, never past the deadline.
 */
const SNOOZE_MS: readonly number[] = [15 * MINUTE, 10 * MINUTE];

export type Command =
  // Recipient
  | { type: "done"; cameraUnavailable?: boolean }
  | { type: "photo_sent"; photoPath: string; takenAt?: Date }
  | { type: "cant_today"; note: string | null }
  | { type: "did_it_later" }
  | { type: "snooze" }
  // Sender
  | { type: "cancel"; note: string | null }
  | { type: "edit"; deadline?: Date; detail?: string | null; finishMode?: FinishMode }
  | { type: "approve" }
  | { type: "re_request"; note: string | null }
  | { type: "new_deadline"; deadline: Date }
  | { type: "sender_handles" }
  // Server
  | { type: "deadline_passed" }
  | { type: "approval_timeout" }
  | { type: "account_deleted"; personId: PersonId };

type CommandType = Command["type"];

export type DomainError =
  | "wrong_status"
  | "not_sender"
  | "not_recipient"
  | "wrong_finish_mode"
  | "deadline_not_in_future"
  | "not_connected"
  | "recipient_unavailable"
  | "snooze_not_allowed";

export type Result<T> = { ok: true; value: T } | { ok: false; error: DomainError };

export interface Outcome {
  task: Task;
  events: TaskEvent[];
  /** The new plan version. Empty steps means poking stopped. */
  plan: PokePlan;
  /** Pending timers of this task are cancelled before `scheduledActions` are added. */
  scheduledActions: ScheduledAction[];
  pushes: PushMessage[];
  /** Photos are deleted when the task closes (docs/product.md, Photo and approval). */
  deletePhotos: boolean;
}

export interface TaskContext {
  sender: Person;
  recipient: Person;
  reachability: Reachability;
  /** This task's events so far, oldest first. */
  history: readonly TaskEvent[];
  now: Date;
}

/**
 * The single exhaustive switch over task statuses: which commands each status accepts and where
 * they lead. Adding a status breaks the build here (switch-exhaustiveness-check).
 */
function transitionsFrom(status: TaskStatus): Partial<Record<CommandType, TaskStatus>> {
  switch (status) {
    case "SCHEDULED":
      return {
        done: "DONE",
        photo_sent: "AWAITING_APPROVAL",
        cant_today: "DROPPED",
        snooze: "SCHEDULED",
        deadline_passed: "MISSED",
        cancel: "CANCELLED",
        edit: "SCHEDULED",
        account_deleted: "CANCELLED",
      };
    case "AWAITING_APPROVAL":
      // Never MISSED: the deadline applies to sending the photo, not to approval.
      return {
        approve: "DONE",
        approval_timeout: "DONE",
        re_request: "REJECTED",
        cancel: "CANCELLED",
        account_deleted: "CANCELLED",
      };
    case "REJECTED":
      return {
        photo_sent: "AWAITING_APPROVAL",
        cant_today: "DROPPED",
        snooze: "REJECTED",
        deadline_passed: "MISSED",
        cancel: "CANCELLED",
        account_deleted: "CANCELLED",
      };
    case "MISSED":
      return {
        // TODO(open-questions#9): "Sonradan yaptım" closes as DONE with late = true.
        did_it_later: "DONE",
        // Only for a photo taken before the deadline whose upload came late (6e); checked below.
        photo_sent: "AWAITING_APPROVAL",
        new_deadline: "SCHEDULED",
        cancel: "CANCELLED",
        account_deleted: "CANCELLED",
      };
    case "DROPPED":
      return {
        new_deadline: "SCHEDULED",
        // TODO(open-questions#8): "Ben hallederim" closes as CANCELLED with reason sender_handles.
        sender_handles: "CANCELLED",
        account_deleted: "CANCELLED",
      };
    case "DONE":
    case "CANCELLED":
      return {};
  }
}

type Actor = "sender" | "recipient" | "server";

function actorOf(type: CommandType): Actor {
  switch (type) {
    case "done":
    case "photo_sent":
    case "cant_today":
    case "did_it_later":
    case "snooze":
      return "recipient";
    case "cancel":
    case "edit":
    case "approve":
    case "re_request":
    case "new_deadline":
    case "sender_handles":
      return "sender";
    case "deadline_passed":
    case "approval_timeout":
    case "account_deleted":
      return "server";
  }
}

const deviceRank: Record<DeviceLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2 };

/** The highest device level that already fired for this task: where poking resumes. */
function resumeLevel(history: readonly TaskEvent[]): DeviceLevel {
  let best: DeviceLevel = "SILENT";
  for (const e of history) {
    const level = e.payload.level;
    if (e.type === "poke_fired" && (level === "SOUND" || level === "FULL_SCREEN")) {
      if (deviceRank[level] > deviceRank[best]) best = level;
    }
  }
  return best;
}

interface Snooze {
  /** 1 for the first snooze of this task. */
  count: number;
  minutes: number;
  /** When poking resumes. */
  until: Date;
  /** No snooze left after this one: "son hakkın". */
  last: boolean;
}

/** The next snooze of a task, or null when none is left or it would run past the deadline. */
function nextSnooze(
  task: Pick<Task, "deadline">,
  ctx: Pick<TaskContext, "history" | "now">,
): Snooze | null {
  const used = ctx.history.filter((e) => e.type === "snoozed").length;
  const length = SNOOZE_MS[used];
  if (length === undefined) return null;
  const until = addMs(ctx.now, length);
  if (until.getTime() >= task.deadline.getTime()) return null;
  return { count: used + 1, minutes: length / MINUTE, until, last: used + 1 === SNOOZE_MS.length };
}

export function isOpen(status: TaskStatus): boolean {
  return status === "SCHEDULED" || status === "REJECTED";
}

/** New plan, timers and photo cleanup for a task that just entered `task.status`. */
function effectsOfStatus(
  task: Task,
  ctx: TaskContext,
  resume: boolean,
  /** Plan as if it were this time: the end of a snooze. Defaults to `ctx.now`. */
  planFrom?: Date,
): Pick<Outcome, "plan" | "scheduledActions" | "deletePhotos"> & { planEvent: TaskEvent | null } {
  const version = task.planVersion;
  const { now } = ctx;
  const stop = { taskId: task.id, version, steps: [] };

  switch (task.status) {
    case "SCHEDULED":
    case "REJECTED": {
      const steps = planPokes({
        task,
        recipient: ctx.recipient,
        sender: ctx.sender,
        reachability: ctx.reachability,
        history: ctx.history,
        now: planFrom ?? now,
        floor: resume ? resumeLevel(ctx.history) : "SILENT",
      });
      const actions: ScheduledAction[] = [
        {
          kind: "deadline",
          taskId: task.id,
          dueAt: task.deadline,
          payload: { planVersion: version },
        },
      ];
      for (const s of steps) {
        actions.push(
          s.level === "HUMAN"
            ? {
                kind: "human_check",
                taskId: task.id,
                dueAt: s.plannedAt,
                payload: { planVersion: version },
              }
            : {
                kind: "lost_poke_check",
                taskId: task.id,
                dueAt: addMs(s.plannedAt, LOST_POKE_GRACE_MS),
                payload: { planVersion: version, seq: s.seq },
              },
        );
      }
      return {
        plan: { taskId: task.id, version, steps },
        scheduledActions: actions,
        deletePhotos: false,
        planEvent: event(task, null, "plan_generated", { version, steps: steps.length }, now),
      };
    }
    case "AWAITING_APPROVAL":
      return {
        plan: stop,
        scheduledActions: [
          {
            kind: "approval_timeout",
            taskId: task.id,
            dueAt: addMs(now, AUTO_APPROVE_AFTER_MS),
            payload: { planVersion: version },
          },
        ],
        deletePhotos: false,
        planEvent: null,
      };
    case "MISSED":
      return {
        plan: stop,
        scheduledActions: [
          {
            kind: "missed_expiry",
            taskId: task.id,
            dueAt: addMs(now, MISSED_CARD_TTL_MS),
            payload: { planVersion: version },
          },
        ],
        deletePhotos: task.finishMode === "photo",
        planEvent: null,
      };
    case "DONE":
    case "DROPPED":
    case "CANCELLED":
      return {
        plan: stop,
        scheduledActions: [],
        deletePhotos: task.finishMode === "photo",
        planEvent: null,
      };
  }
}

function event(
  task: Pick<Task, "id">,
  actorId: PersonId | null,
  type: TaskEventType,
  payload: Record<string, unknown>,
  at: Date,
): TaskEvent {
  return { taskId: task.id, actorId, type, payload, at };
}

function push(
  to: PersonId,
  hint: PushMessage["hint"],
  task: Task,
  e: PushMessage["event"],
): PushMessage {
  return { to, hint, taskId: task.id, event: e };
}

/**
 * Who is told about an event. Pushes are hints; the bell and lists come from sync.
 * Done without a photo sits in the bell with no push to the sender (docs/product.md).
 */
function pushesFor(task: Task, e: TaskEvent): PushMessage[] {
  const { senderId, recipientId } = task;
  const self = senderId === recipientId;
  switch (e.type) {
    // After a snooze the device rings the snoozed step again itself; the hint brings the shifted
    // rest of the plan.
    case "created":
    case "edited":
    case "re_requested":
    case "snoozed":
      return [push(recipientId, "plan_changed", task, e.type)];
    case "photo_sent":
      return self ? [] : [push(senderId, "sync", task, e.type)];
    case "dropped":
      return self ? [] : [push(senderId, "sync", task, e.type)];
    case "approved":
      return e.payload.auto === true && !self
        ? [push(recipientId, "sync", task, e.type), push(senderId, "sync", task, e.type)]
        : [push(recipientId, "sync", task, e.type)];
    case "missed":
      return self
        ? [push(recipientId, "sync", task, e.type)]
        : [push(recipientId, "sync", task, e.type), push(senderId, "sync", task, e.type)];
    case "cancelled": {
      if (e.payload.reason === "account_deleted") {
        const other = e.actorId === senderId ? recipientId : senderId;
        return self ? [] : [push(other, "task_cancelled", task, e.type)];
      }
      return self ? [] : [push(recipientId, "task_cancelled", task, e.type)];
    }
    case "human_notified":
      return [push(senderId, "sync", task, e.type)];
    case "poke_lost":
      return [push(recipientId, "sync", task, e.type)];
    case "done":
    case "plan_generated":
    case "poke_fired":
    case "permission_missing":
      return [];
  }
}

function eventFor(
  task: Task,
  cmd: Command,
  actorId: PersonId | null,
  now: Date,
  snooze: Snooze | null,
): TaskEvent[] {
  switch (cmd.type) {
    case "snooze":
      return snooze
        ? [
            event(
              task,
              actorId,
              "snoozed",
              { count: snooze.count, minutes: snooze.minutes, until: snooze.until.toISOString() },
              now,
            ),
          ]
        : [];
    case "done":
      return [
        event(task, actorId, "done", { cameraUnavailable: cmd.cameraUnavailable === true }, now),
      ];
    case "photo_sent": {
      const sent = event(task, actorId, "photo_sent", { photoPath: cmd.photoPath }, now);
      // TODO(open-questions#7): a photo on a task to self closes it, no approval step.
      return task.senderId === task.recipientId
        ? [sent, event(task, actorId, "done", { self: true }, now)]
        : [sent];
    }
    case "cant_today":
      return [event(task, actorId, "dropped", { note: cmd.note }, now)];
    case "did_it_later":
      return [event(task, actorId, "done", { late: true }, now)];
    case "cancel":
      return [
        event(task, actorId, "cancelled", { reason: "sender_cancelled", note: cmd.note }, now),
      ];
    case "sender_handles":
      return [
        event(task, actorId, "cancelled", { reason: "sender_handles" satisfies CancelReason }, now),
      ];
    case "edit":
      return [
        event(
          task,
          actorId,
          "edited",
          { deadline: cmd.deadline?.toISOString(), detail: cmd.detail, finishMode: cmd.finishMode },
          now,
        ),
      ];
    case "new_deadline":
      return [
        event(
          task,
          actorId,
          "edited",
          { deadline: cmd.deadline.toISOString(), reopened: true },
          now,
        ),
      ];
    case "approve":
      return [event(task, actorId, "approved", { auto: false }, now)];
    case "approval_timeout":
      return [event(task, null, "approved", { auto: true }, now)];
    case "re_request":
      return [event(task, actorId, "re_requested", { note: cmd.note }, now)];
    case "deadline_passed":
      return [event(task, null, "missed", {}, now)];
    case "account_deleted":
      return [
        event(
          task,
          cmd.personId,
          "cancelled",
          { reason: "account_deleted" satisfies CancelReason },
          now,
        ),
      ];
  }
}

function checkActor(task: Task, cmd: Command, actorId: PersonId | null): DomainError | null {
  switch (actorOf(cmd.type)) {
    case "sender":
      return actorId === task.senderId ? null : "not_sender";
    case "recipient":
      return actorId === task.recipientId ? null : "not_recipient";
    case "server":
      return null;
  }
}

/** Apply a command to a task. `actorId` is null for server commands. */
/** A photo taken (on the device clock) before the deadline and not in the future. */
export function takenInTime(task: Task, takenAt: Date | undefined, now: Date): boolean {
  return (
    takenAt !== undefined &&
    takenAt.getTime() <= task.deadline.getTime() &&
    takenAt.getTime() <= now.getTime()
  );
}

export function applyCommand(
  task: Task,
  cmd: Command,
  actorId: PersonId | null,
  ctx: TaskContext,
): Result<Outcome> {
  const { now } = ctx;
  let target = transitionsFrom(task.status)[cmd.type];
  if (target === undefined) return { ok: false, error: "wrong_status" };
  const actorError = checkActor(task, cmd, actorId);
  if (actorError) return { ok: false, error: actorError };

  const next: Task = { ...task };
  let snooze: Snooze | null = null;
  switch (cmd.type) {
    case "snooze":
      snooze = nextSnooze(task, ctx);
      if (!snooze) return { ok: false, error: "snooze_not_allowed" };
      break;
    case "done":
      // If the camera permission is denied, a photo task falls back to plain "Bitti".
      if (task.finishMode === "photo" && cmd.cameraUnavailable !== true) {
        return { ok: false, error: "wrong_finish_mode" };
      }
      break;
    case "photo_sent":
      if (task.finishMode !== "photo") return { ok: false, error: "wrong_finish_mode" };
      // The deadline counts from the send attempt (docs/product.md, Errors): a photo taken in time whose upload
      // was late still counts after the task was marked missed.
      if (task.status === "MISSED" && !takenInTime(task, cmd.takenAt, now)) {
        return { ok: false, error: "wrong_status" };
      }
      if (task.senderId === task.recipientId) target = "DONE";
      break;
    case "did_it_later":
      next.late = true;
      break;
    case "edit":
      if (cmd.deadline !== undefined) {
        if (cmd.deadline.getTime() <= now.getTime()) {
          return { ok: false, error: "deadline_not_in_future" };
        }
        next.deadline = cmd.deadline;
      }
      if (cmd.detail !== undefined) next.detail = cmd.detail;
      if (cmd.finishMode !== undefined) next.finishMode = cmd.finishMode;
      break;
    case "new_deadline":
      if (cmd.deadline.getTime() <= now.getTime()) {
        return { ok: false, error: "deadline_not_in_future" };
      }
      next.deadline = cmd.deadline;
      break;
    case "account_deleted":
      if (cmd.personId !== task.senderId && cmd.personId !== task.recipientId) {
        return { ok: false, error: "not_sender" };
      }
      break;
    case "cant_today":
    case "cancel":
    case "approve":
    case "re_request":
    case "sender_handles":
    case "deadline_passed":
    case "approval_timeout":
      break;
  }

  if (target !== task.status) next.statusChangedAt = now;
  next.status = target;
  next.planVersion = task.planVersion + 1;

  const events = eventFor(next, cmd, actorId, now, snooze);
  // After a re-request or a snooze poking resumes at the level it had reached; a snooze also starts
  // the new plan where the snooze ends.
  const effects = effectsOfStatus(
    next,
    ctx,
    cmd.type === "re_request" || snooze !== null,
    snooze?.until,
  );
  if (effects.planEvent) events.push(effects.planEvent);

  return {
    ok: true,
    value: {
      task: next,
      events,
      plan: effects.plan,
      scheduledActions: effects.scheduledActions,
      pushes: dedupe(events.flatMap((e) => pushesFor(next, e))),
      deletePhotos: effects.deletePhotos,
    },
  };
}

function dedupe(pushes: PushMessage[]): PushMessage[] {
  const seen = new Set<string>();
  return pushes.filter((p) => {
    const key = `${p.to}|${p.hint}|${String(p.taskId)}|${p.event}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export interface CreateTaskInput {
  id: TaskId;
  sender: Person;
  recipient: Person;
  /** The sender's edge to the recipient. Not needed for a task to self. */
  edge: Edge | null;
  title: string;
  detail: string | null;
  deadline: Date;
  finishMode: FinishMode;
  rawText: string | null;
  reachability: Reachability;
  now: Date;
}

/** Create a task (always SCHEDULED) with its first plan. */
export function createTask(input: CreateTaskInput): Result<Outcome> {
  const { sender, recipient, now } = input;
  const self = sender.id === recipient.id;
  if (recipient.deletedAt !== null || sender.deletedAt !== null) {
    return { ok: false, error: "recipient_unavailable" };
  }
  // Tasks cannot be written to a pending (not yet joined) person.
  if (
    !self &&
    (input.edge?.status !== "active" ||
      input.edge.fromPersonId !== sender.id ||
      input.edge.toPersonId !== recipient.id)
  ) {
    return { ok: false, error: "not_connected" };
  }
  if (input.deadline.getTime() <= now.getTime()) {
    return { ok: false, error: "deadline_not_in_future" };
  }

  const task: Task = {
    id: input.id,
    senderId: sender.id,
    recipientId: recipient.id,
    title: input.title,
    detail: input.detail,
    deadline: input.deadline,
    finishMode: input.finishMode,
    rawText: input.rawText,
    status: "SCHEDULED",
    late: false,
    planVersion: 1,
    statusChangedAt: now,
  };
  const created = event(task, sender.id, "created", {}, now);
  const effects = effectsOfStatus(
    task,
    { sender, recipient, reachability: input.reachability, history: [], now },
    false,
  );
  const events = effects.planEvent ? [created, effects.planEvent] : [created];
  return {
    ok: true,
    value: {
      task,
      events,
      plan: effects.plan,
      scheduledActions: effects.scheduledActions,
      pushes: pushesFor(task, created),
      deletePhotos: false,
    },
  };
}
