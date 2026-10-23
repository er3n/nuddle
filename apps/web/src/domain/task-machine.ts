// Task state machine (docs/domain.md, Task states). Every transition is a pure function returning
// the new task, the events to append, the new plan version, the server timers and the push hints.
// The caller persists all of it in one transaction (ADR 0006, 0007).
import { planPokes } from "./plan-pokes";
import type { Reachability } from "./reachability";
import { DAY, HOUR, MINUTE, addMs } from "./time";
import type { RepeatRule } from "./repeat";
import { leadMinutesOf, MAX_LEAD_MINUTES, retime, scheduleOf, timingFor } from "./timing";
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
  TaskContact,
  TaskId,
  PokeStep,
  TaskStatus,
  Timing,
} from "./types";

export const AUTO_APPROVE_AFTER_MS = 3 * HOUR;
export const MISSED_CARD_TTL_MS = 1 * DAY;
/**
 * Photos are deleted right after a photo task closes; this timer deletes them again later, and
 * keeps retrying, so a Storage outage at that moment cannot leave them behind (NUD-25).
 */
export const PHOTO_CLEANUP_RETRY_MS = 15 * MINUTE;
/** (proposed) A step with no fired report this long after its planned time counts as lost. */
const LOST_POKE_GRACE_MS = 5 * MINUTE;
/**
 * Snooze lengths in order; their count is the limit, the last one is "son hakkın".
 * TODO(open-questions#1): 2 snoozes, 15 then 10 minutes, never past the deadline.
 */
const SNOOZE_MS: readonly number[] = [15 * MINUTE, 10 * MINUTE];

export type Command =
  // Recipient
  | { type: "done"; cameraUnavailable?: boolean; doneAt?: Date }
  | { type: "photo_sent"; photoPath: string; takenAt?: Date }
  | { type: "cant_today"; note: string | null }
  | { type: "did_it_later" }
  | { type: "snooze" }
  /** When the pokes may begin, chosen by the recipient (NUD-78); the deadline stays. */
  | { type: "set_start"; startAt: Date }
  // Sender
  | { type: "cancel"; note: string | null }
  /** `deadline` here and in new_deadline is the time the person picked; an `at` task keeps its grace. */
  | { type: "edit"; deadline?: Date; detail?: string | null; finishMode?: FinishMode }
  /**
   * Kime changed on an open task (NUD-114): this task closes as `reassigned`, and the caller
   * creates the same task for `recipientId` in the same transaction.
   */
  | { type: "reassign"; recipientId: PersonId }
  | { type: "approve" }
  | { type: "re_request"; note: string | null }
  | { type: "new_deadline"; deadline: Date }
  | { type: "sender_handles" }
  // Server
  | { type: "deadline_passed" }
  | { type: "approval_timeout" }
  | { type: "account_deleted"; personId: PersonId }
  | { type: "connection_removed"; personId: PersonId }
  /** Quiet hours or a human channel switch changed: same status, a new plan. */
  | { type: "replan" };

type CommandType = Command["type"];

export type DomainError =
  | "wrong_status"
  | "not_sender"
  | "not_recipient"
  | "wrong_finish_mode"
  | "deadline_not_in_future"
  | "start_not_in_window"
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
  /** The sender's edge to the recipient (5b switch); absent for a task to self. */
  senderEdge?: Pick<Edge, "notifyMe"> | null;
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
        set_start: "SCHEDULED",
        deadline_passed: "MISSED",
        cancel: "CANCELLED",
        edit: "SCHEDULED",
        reassign: "CANCELLED",
        account_deleted: "CANCELLED",
        connection_removed: "CANCELLED",
        replan: "SCHEDULED",
      };
    case "AWAITING_APPROVAL":
      // Never MISSED: the deadline applies to sending the photo, not to approval.
      return {
        approve: "DONE",
        approval_timeout: "DONE",
        re_request: "REJECTED",
        cancel: "CANCELLED",
        account_deleted: "CANCELLED",
        connection_removed: "CANCELLED",
      };
    case "REJECTED":
      return {
        photo_sent: "AWAITING_APPROVAL",
        cant_today: "DROPPED",
        snooze: "REJECTED",
        deadline_passed: "MISSED",
        cancel: "CANCELLED",
        account_deleted: "CANCELLED",
        connection_removed: "CANCELLED",
        replan: "REJECTED",
      };
    case "MISSED":
      return {
        // TODO(open-questions#9): "Sonradan yaptım" closes as DONE with late = true.
        did_it_later: "DONE",
        // Only for a photo taken before the deadline whose upload came late (6e); checked below.
        photo_sent: "AWAITING_APPROVAL",
        // Only for a Bitti tapped offline before the deadline that reached the server after it (6a); checked below.
        done: "DONE",
        new_deadline: "SCHEDULED",
        cancel: "CANCELLED",
        account_deleted: "CANCELLED",
        connection_removed: "CANCELLED",
      };
    case "DROPPED":
      return {
        new_deadline: "SCHEDULED",
        // TODO(open-questions#8): "Ben hallederim" closes as CANCELLED with reason sender_handles.
        sender_handles: "CANCELLED",
        account_deleted: "CANCELLED",
        connection_removed: "CANCELLED",
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
    case "set_start":
      return "recipient";
    case "cancel":
    case "edit":
    case "reassign":
    case "approve":
    case "re_request":
    case "new_deadline":
    case "sender_handles":
      return "sender";
    case "deadline_passed":
    case "approval_timeout":
    case "account_deleted":
    case "connection_removed":
    case "replan":
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

/** Time left to do the task after a snooze ends, and the shortest snooze worth offering (NUD-63). */
const AFTER_SNOOZE_MS = 5 * MINUTE;
const MIN_SNOOZE_MS = 5 * MINUTE;

/**
 * The next snooze of a task, or null when none is left or too little time is: 15 then 10 minutes,
 * shortened (whole minutes) so 5 minutes remain before the deadline, never under 5 (open-questions#1).
 * The device applies the same rule to label Ertele (`SnoozeRules`).
 */
function nextSnooze(
  task: Pick<Task, "deadline">,
  ctx: Pick<TaskContext, "history" | "now">,
): Snooze | null {
  const used = ctx.history.filter((e) => e.type === "snoozed").length;
  const planned = SNOOZE_MS[used];
  if (planned === undefined) return null;
  const room = task.deadline.getTime() - ctx.now.getTime() - AFTER_SNOOZE_MS;
  const length = Math.min(planned, Math.floor(room / MINUTE) * MINUTE);
  if (length < MIN_SNOOZE_MS) return null;
  const until = addMs(ctx.now, length);
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
  /** Steps the LLM planner proposed and `checkPlan` passed (ADR 0017); plan v0 otherwise. */
  planned?: PokeStep[],
): Pick<Outcome, "plan" | "scheduledActions" | "deletePhotos"> & { planEvent: TaskEvent | null } {
  const version = task.planVersion;
  const { now } = ctx;
  const stop = { taskId: task.id, version, steps: [] };

  switch (task.status) {
    case "SCHEDULED":
    case "REJECTED": {
      const steps =
        planned ??
        planPokes({
          task,
          recipient: ctx.recipient,
          sender: ctx.sender,
          senderEdge: ctx.senderEdge,
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
        planEvent: event(
          task,
          null,
          "plan_generated",
          { version, steps: steps.length, source: planned ? "llm" : "v0" },
          now,
        ),
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
          ...photoCleanup(task, now),
        ],
        deletePhotos: hasPhotos(task),
        planEvent: null,
      };
    case "DONE":
    case "DROPPED":
    case "CANCELLED":
      return {
        plan: stop,
        scheduledActions: photoCleanup(task, now),
        deletePhotos: hasPhotos(task),
        planEvent: null,
      };
  }
}

/**
 * Whether the task's folder may hold files: the recipient's photos of a photo task, or the
 * sender's example photo (NUD-138). Both go when the task closes.
 */
function hasPhotos(task: Task): boolean {
  return task.finishMode === "photo" || task.examplePhotoPath !== null;
}

/** The later retry of the photo deletion for a task with photos that closes (NUD-25). */
function photoCleanup(task: Task, now: Date): ScheduledAction[] {
  return hasPhotos(task)
    ? [
        {
          kind: "photo_cleanup",
          taskId: task.id,
          dueAt: addMs(now, PHOTO_CLEANUP_RETRY_MS),
          payload: {},
        },
      ]
    : [];
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

/** Events the other phone shows nothing for: the plan is hidden, and a ring is the recipient's own. */
const UNSEEN: ReadonlySet<TaskEventType> = new Set([
  "plan_generated",
  "poke_fired",
  "permission_missing",
]);

/**
 * Who is told about an event. Pushes are hints, never notifications by themselves: the phone syncs,
 * and the bell, lists and notifications come from sync (docs/architecture.md, Push). Every event
 * either person sees reaches both of them, so both lists follow at once (NUD-59); which hint a
 * person gets first comes from `hintsFor`.
 */
function pushesFor(task: Task, e: TaskEvent): PushMessage[] {
  const first = hintsFor(task, e);
  if (UNSEEN.has(e.type)) return first;
  const told = new Set(first.map((p) => p.to));
  const rest = [task.senderId, task.recipientId]
    .filter((to, i, all) => all.indexOf(to) === i && !told.has(to))
    .map((to) => push(to, "sync", task, e.type));
  return [...first, ...rest];
}

/** The hint that matters for an event: a new plan for the recipient, or a sync for whoever must see it. */
function hintsFor(task: Task, e: TaskEvent): PushMessage[] {
  const { senderId, recipientId } = task;
  const self = senderId === recipientId;
  switch (e.type) {
    // After a snooze the device rings the snoozed step again itself; the hint brings the shifted
    // rest of the plan. A moved start (NUD-78) is a new plan too.
    case "created":
    case "edited":
    case "re_requested":
    case "snoozed":
    case "start_set":
      return [push(recipientId, "plan_changed", task, e.type)];
    case "photo_sent":
      return self ? [] : [push(senderId, "sync", task, e.type)];
    // The recipient's phone fetches the sender's picture with the next sync (NUD-138).
    case "example_photo_set":
      return self ? [] : [push(recipientId, "sync", task, e.type)];
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
      // Removing a connection tells nobody (5b): both sides only sync, so the alarms stop on every
      // phone, the remover's included.
      if (e.payload.reason === "connection_removed") {
        return [push(senderId, "sync", task, e.type), push(recipientId, "sync", task, e.type)];
      }
      if (e.payload.reason === "account_deleted") {
        const other = e.actorId === senderId ? recipientId : senderId;
        return self ? [] : [push(other, "task_cancelled", task, e.type)];
      }
      return self ? [] : [push(recipientId, "task_cancelled", task, e.type)];
    }
    case "human_notified":
      return [push(senderId, "sync", task, e.type)];
    case "delivered":
    case "seen":
      return self ? [] : [push(senderId, "sync", task, e.type)];
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
    case "set_start":
      return [event(task, actorId, "start_set", { startAt: cmd.startAt.toISOString() }, now)];
    case "cancel":
      return [
        event(task, actorId, "cancelled", { reason: "sender_cancelled", note: cmd.note }, now),
      ];
    case "sender_handles":
      return [
        event(task, actorId, "cancelled", { reason: "sender_handles" satisfies CancelReason }, now),
      ];
    case "reassign":
      return [
        event(
          task,
          actorId,
          "cancelled",
          { reason: "reassigned" satisfies CancelReason, note: null, to: cmd.recipientId },
          now,
        ),
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
      return task.finishMode === "notice"
        ? [event(task, null, "done", { notice: true }, now)]
        : [event(task, null, "missed", {}, now)];
    case "account_deleted":
    case "connection_removed":
      return [
        event(task, cmd.personId, "cancelled", { reason: cmd.type satisfies CancelReason }, now),
      ];
    case "replan":
      return [];
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
/** A photo taken or a Bitti tapped (on the device clock) before the deadline and not in the future. */
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
      // A one-time message (NUD-79) is not postponed; "Tamam" is the only answer.
      if (task.finishMode === "notice") return { ok: false, error: "wrong_finish_mode" };
      snooze = nextSnooze(task, ctx);
      if (!snooze) return { ok: false, error: "snooze_not_allowed" };
      break;
    case "done":
      // If the camera permission is denied, a photo task falls back to plain "Bitti".
      if (task.finishMode === "photo" && cmd.cameraUnavailable !== true) {
        return { ok: false, error: "wrong_finish_mode" };
      }
      if (task.status === "MISSED" && !takenInTime(task, cmd.doneAt, now)) {
        return { ok: false, error: "wrong_status" };
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
    case "set_start": {
      // Between now and the task's time; for an `at` task inside the lead limit (NUD-78).
      const time = task.remindAt ?? task.deadline;
      const at = cmd.startAt.getTime();
      if (at <= now.getTime() || at > time.getTime()) {
        return { ok: false, error: "start_not_in_window" };
      }
      if (task.remindAt !== null) {
        const lead = Math.round((task.remindAt.getTime() - at) / MINUTE);
        if (lead > MAX_LEAD_MINUTES) return { ok: false, error: "start_not_in_window" };
        next.leadMinutes = lead;
        next.pokeFrom = null;
      } else {
        next.pokeFrom = cmd.startAt;
      }
      break;
    }
    case "edit":
      if (cmd.deadline !== undefined) {
        if (cmd.deadline.getTime() <= now.getTime()) {
          return { ok: false, error: "deadline_not_in_future" };
        }
        Object.assign(next, retime(task, cmd.deadline, now));
      }
      if (cmd.detail !== undefined) next.detail = cmd.detail;
      if (cmd.finishMode !== undefined) next.finishMode = cmd.finishMode;
      break;
    case "new_deadline":
      if (cmd.deadline.getTime() <= now.getTime()) {
        return { ok: false, error: "deadline_not_in_future" };
      }
      Object.assign(next, retime(task, cmd.deadline, now));
      break;
    case "account_deleted":
    case "connection_removed":
      if (cmd.personId !== task.senderId && cmd.personId !== task.recipientId) {
        return { ok: false, error: "not_sender" };
      }
      break;
    case "replan":
      break;
    case "cant_today":
      if (task.finishMode === "notice") return { ok: false, error: "wrong_finish_mode" };
      break;
    case "deadline_passed":
      // A message's time passing is not a miss: it was delivered or it is over (NUD-79).
      if (task.finishMode === "notice") target = "DONE";
      break;
    case "cancel":
    case "approve":
    case "re_request":
    case "sender_handles":
    case "approval_timeout":
      break;
    case "reassign":
      if (cmd.recipientId === task.recipientId) return { ok: false, error: "wrong_status" };
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
    cmd.type === "re_request" ||
      cmd.type === "replan" ||
      cmd.type === "set_start" ||
      snooze !== null,
    snooze?.until,
  );
  if (effects.planEvent) events.push(effects.planEvent);
  // The example photo's file goes with the folder: the task no longer points at it (NUD-138).
  if (effects.deletePhotos) next.examplePhotoPath = null;

  return {
    ok: true,
    value: {
      task: next,
      events,
      plan: effects.plan,
      scheduledActions: effects.scheduledActions,
      pushes:
        cmd.type === "replan"
          ? [push(next.recipientId, "plan_changed", next, "plan_generated")]
          : dedupe(events.flatMap((e) => pushesFor(next, e))),
      deletePhotos: effects.deletePhotos,
    },
  };
}

/**
 * Replace plan v0 of an open task with the LLM planner's checked steps (ADR 0017). The caller
 * checks that the task is still on `expectedVersion`; a newer version means something happened
 * meanwhile and that plan wins.
 */
export function adoptPlan(
  task: Task,
  steps: PokeStep[],
  expectedVersion: number,
  ctx: TaskContext,
): Result<Outcome> {
  if (!isOpen(task.status) || task.planVersion !== expectedVersion || steps.length === 0) {
    return { ok: false, error: "wrong_status" };
  }
  const next: Task = { ...task, planVersion: task.planVersion + 1 };
  const effects = effectsOfStatus(next, ctx, false, undefined, steps);
  return {
    ok: true,
    value: {
      task: next,
      events: effects.planEvent ? [effects.planEvent] : [],
      plan: effects.plan,
      scheduledActions: effects.scheduledActions,
      pushes: [push(next.recipientId, "plan_changed", next, "plan_generated")],
      deletePhotos: false,
    },
  };
}

/** The statuses in which the sender may still attach an example photo (NUD-138). */
const EXAMPLE_PHOTO_STATUSES: ReadonlySet<TaskStatus> = new Set([
  "SCHEDULED",
  "REJECTED",
  "AWAITING_APPROVAL",
]);

/** Why [actorId] may not attach an example photo to the task now, or null when they may. */
export function examplePhotoRefusal(task: Task, actorId: PersonId): DomainError | null {
  if (actorId !== task.senderId) return "not_sender";
  if (!EXAMPLE_PHOTO_STATUSES.has(task.status)) return "wrong_status";
  return null;
}

/**
 * The sender's example photo is uploaded (NUD-138, docs/domain.md, Example photo): the task keeps
 * its path and the recipient's phone syncs. Never proof: no status, plan or timer changes. A path
 * that replaces another is returned as `replaced` so the caller deletes that file.
 */
export function setExamplePhoto(
  task: Task,
  actorId: PersonId,
  photoPath: string,
  now: Date,
): Result<{ task: Task; events: TaskEvent[]; pushes: PushMessage[]; replaced: string | null }> {
  const refused = examplePhotoRefusal(task, actorId);
  if (refused) return { ok: false, error: refused };
  if (task.examplePhotoPath === photoPath) {
    return { ok: true, value: { task, events: [], pushes: [], replaced: null } };
  }
  const next: Task = { ...task, examplePhotoPath: photoPath };
  const set = event(next, actorId, "example_photo_set", {}, now);
  return {
    ok: true,
    value: {
      task: next,
      events: [set],
      pushes: pushesFor(next, set),
      replaced: task.examplePhotoPath,
    },
  };
}

/**
 * "Kaldır" on the recipient's missed card (NUD-49): hides it from their list at once. Not a status change, so no
 * event, plan or timer; the sender still sees the task as missed.
 */
export function removeMissedCard(task: Task, actorId: PersonId, now: Date): Result<Task> {
  if (actorId !== task.recipientId) return { ok: false, error: "not_recipient" };
  if (task.status !== "MISSED") return { ok: false, error: "wrong_status" };
  return { ok: true, value: { ...task, removedByRecipientAt: now } };
}

/**
 * The recipient's phone says the task reached it, or that the recipient has seen it (NUD-68): the sender's tick.
 * Each is logged once; seen implies delivered. Nothing for a task to self or a closed task. [logged] are the event
 * types the task already has.
 */
export function acknowledge(
  task: Task,
  actorId: PersonId,
  seen: boolean,
  logged: ReadonlySet<TaskEventType>,
  now: Date,
): Result<{ events: TaskEvent[]; pushes: PushMessage[] }> {
  if (actorId !== task.recipientId) return { ok: false, error: "not_recipient" };
  if (task.senderId === task.recipientId || !isOpen(task.status)) {
    return { ok: true, value: { events: [], pushes: [] } };
  }
  const wanted: TaskEventType[] = seen ? ["delivered", "seen"] : ["delivered"];
  const events = wanted
    .filter((type) => !logged.has(type))
    .map((type): TaskEvent => ({ taskId: task.id, actorId, type, payload: {}, at: now }));
  const last = events.at(-1);
  return {
    ok: true,
    value: {
      events,
      pushes: last ? hintsFor(task, last) : [],
    },
  };
}

/**
 * One hint per person and task: any hint makes the phone sync, so a second one only costs a push. A
 * named hint (`plan_changed`, `task_cancelled`) wins over a plain `sync`.
 */
function dedupe(pushes: PushMessage[]): PushMessage[] {
  const kept = new Map<string, PushMessage>();
  for (const p of pushes) {
    const key = `${p.to}|${String(p.taskId)}`;
    const before = kept.get(key);
    if (!before || (before.hint === "sync" && p.hint !== "sync")) kept.set(key, p);
  }
  return [...kept.values()];
}

export interface CreateTaskInput {
  id: TaskId;
  sender: Person;
  recipient: Person;
  /** The sender's edge to the recipient. Not needed for a task to self. */
  edge: Edge | null;
  title: string;
  detail: string | null;
  /** The time the person picked: the deadline of a `by` task, the moment of an `at` task. */
  deadline: Date;
  /** Defaults to `by`. */
  timing?: Timing;
  /** How long after the moment an `at` task is missed (ADR 0017). */
  missAfterMinutes?: number | null;
  /** How long before the moment the person must start (NUD-70); ignored for `by`. */
  leadMinutes?: number | null;
  finishMode: FinishMode;
  rawText: string | null;
  /** The rule of a repeating task (ADR 0021); the first occurrence starts the series. */
  repeat?: RepeatRule | null;
  /** Set when this is the next occurrence of an existing series. */
  seriesId?: TaskId | null;
  /** Whom to call or message from the poke (NUD-128), already checked with `keepContact`. */
  contact?: TaskContact | null;
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
  // A notice without a time has the moment it is written as its time (NUD-140).
  const notAhead =
    input.finishMode === "notice"
      ? input.deadline.getTime() < now.getTime()
      : input.deadline.getTime() <= now.getTime();
  if (notAhead) return { ok: false, error: "deadline_not_in_future" };

  // A `by` time a few minutes away is a moment (NUD-116).
  const timing = timingFor(input.timing ?? "by", input.deadline, now);
  const task: Task = {
    id: input.id,
    senderId: sender.id,
    recipientId: recipient.id,
    title: input.title,
    detail: input.detail,
    timing,
    ...scheduleOf(timing, input.deadline, input.missAfterMinutes),
    leadMinutes: leadMinutesOf(timing, input.leadMinutes),
    pokeFrom: null,
    finishMode: input.finishMode,
    rawText: input.rawText,
    repeat: input.repeat ?? null,
    seriesId: input.repeat ? (input.seriesId ?? input.id) : null,
    contact: input.contact ?? null,
    // Uploaded after the task exists, then set with set_example_photo (NUD-138).
    examplePhotoPath: null,
    status: "SCHEDULED",
    late: false,
    planVersion: 1,
    statusChangedAt: now,
    removedByRecipientAt: null,
  };
  const created = event(
    task,
    sender.id,
    "created",
    task.seriesId === null ? {} : { seriesId: task.seriesId, repeat: task.repeat },
    now,
  );
  const effects = effectsOfStatus(
    task,
    {
      sender,
      recipient,
      senderEdge: input.edge,
      reachability: input.reachability,
      history: [],
      now,
    },
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
