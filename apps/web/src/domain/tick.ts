// Server timers (ADR 0007): one pure handler per `scheduled_actions` kind, and `tick(now)` which
// claims due rows through a port, runs the handler and hands the result back for persistence.
// Every handler is idempotent: a stale or repeated action (old plan version, task already moved
// on) is a no-op.
import type { Device } from "./reachability";
import { deriveReachability } from "./reachability";
import { applyCommand, isOpen, type Outcome, type TaskContext } from "./task-machine";
import type { PersonId, PokePlan, PushMessage, ScheduledAction, Task, TaskEvent } from "./types";

export interface ClaimedAction extends ScheduledAction {
  id: string;
}

export interface ActionContext extends Omit<TaskContext, "reachability"> {
  task: Task;
  /** The task's current plan version. */
  plan: PokePlan;
  /** The recipient's devices. */
  recipientDevices: readonly Device[];
}

export type ActionResult =
  | { kind: "transition"; outcome: Outcome }
  | { kind: "notify"; events: TaskEvent[]; pushes: PushMessage[] }
  /** Delete the task's photos; on failure the timer runs again later (NUD-25). */
  | { kind: "delete_photos"; taskId: Task["id"] }
  /** Delete what is left of a deleted account (NUD-89). */
  | { kind: "purge_account"; personId: PersonId }
  | { kind: "noop" };

const noop: ActionResult = { kind: "noop" };

function isCurrent(action: ScheduledAction, task: Task): boolean {
  return action.payload.planVersion === task.planVersion;
}

export function handleAction(action: ScheduledAction, ctx: ActionContext): ActionResult {
  const { task, now } = ctx;
  const taskCtx: TaskContext = { ...ctx, reachability: deriveReachability(ctx.recipientDevices) };

  switch (action.kind) {
    case "deadline": {
      if (!isCurrent(action, task) || !isOpen(task.status)) return noop;
      const r = applyCommand(task, { type: "deadline_passed" }, null, taskCtx);
      return r.ok ? { kind: "transition", outcome: r.value } : noop;
    }
    case "approval_timeout": {
      if (!isCurrent(action, task) || task.status !== "AWAITING_APPROVAL") return noop;
      const r = applyCommand(task, { type: "approval_timeout" }, null, taskCtx);
      return r.ok ? { kind: "transition", outcome: r.value } : noop;
    }
    case "human_check": {
      // The full screen step went unanswered and the deadline is close: tell the sender
      // ("Eren'e ulaşamıyorum"). Poking is not stopped.
      if (!isCurrent(action, task) || !isOpen(task.status)) return noop;
      return {
        kind: "notify",
        events: [
          {
            taskId: task.id,
            actorId: null,
            type: "human_notified",
            payload: { to: task.senderId, reachability: taskCtx.reachability },
            at: now,
          },
        ],
        pushes: [{ to: task.senderId, hint: "sync", taskId: task.id, event: "human_notified" }],
      };
    }
    case "lost_poke_check": {
      if (!isCurrent(action, task) || !isOpen(task.status)) return noop;
      const step = ctx.plan.steps.find((s) => s.seq === action.payload.seq);
      if (!step || step.firedAt !== null) return noop;
      // Lost only if a signed-in device was seen online after the planned time; an offline phone
      // is not evidence of a lost alarm.
      const seenOnline = ctx.recipientDevices.some(
        (d) => d.fid !== null && d.lastSeenAt.getTime() >= step.plannedAt.getTime(),
      );
      if (!seenOnline) return noop;
      return {
        kind: "notify",
        events: [
          {
            taskId: task.id,
            actorId: null,
            type: "poke_lost",
            payload: { seq: step.seq, level: step.level, plannedAt: step.plannedAt.toISOString() },
            at: now,
          },
        ],
        pushes: [{ to: task.recipientId, hint: "sync", taskId: task.id, event: "poke_lost" }],
      };
    }
    case "missed_expiry":
      // The missed card leaves the recipient's list (see `visibleToRecipient`); a sync hint
      // refreshes it.
      if (!isCurrent(action, task) || task.status !== "MISSED") return noop;
      return {
        kind: "notify",
        events: [],
        pushes: [{ to: task.recipientId, hint: "sync", taskId: task.id, event: "missed" }],
      };
    case "photo_cleanup":
      // Only while the task stays closed: a late photo (6e) or a new deadline opened it again,
      // and its next close schedules another cleanup.
      return isOpen(task.status) || task.status === "AWAITING_APPROVAL"
        ? noop
        : { kind: "delete_photos", taskId: task.id };
    case "permission_request_cooldown":
      // The cooldown is enforced when a social permission request is made; the row only marks
      // when it ends. Social permission requests arrive with their feature slice.
      return noop;
    case "account_purge":
      // Has no task; `tick` handles it before loading one.
      return noop;
  }
}

/** The purge of a deleted account, or null for an action that belongs to a task. */
function personAction(action: ScheduledAction): ActionResult | null {
  if (action.kind !== "account_purge") return null;
  const personId = action.payload.personId;
  return typeof personId === "string" ? { kind: "purge_account", personId } : noop;
}

/** Whether the recipient's task list still shows the task. Missed cards stay for one day. */
export function visibleToRecipient(task: Task, missedCardTtlMs: number, now: Date): boolean {
  switch (task.status) {
    case "MISSED":
      // A removal counts for this miss only: a task missed again after a new time shows again.
      return (
        (task.removedByRecipientAt === null ||
          task.removedByRecipientAt.getTime() < task.statusChangedAt.getTime()) &&
        now.getTime() < task.statusChangedAt.getTime() + missedCardTtlMs
      );
    case "SCHEDULED":
    case "AWAITING_APPROVAL":
    case "REJECTED":
      return true;
    case "DONE":
    case "DROPPED":
    case "CANCELLED":
      return false;
  }
}

export interface TickPort {
  /** Claim due, not yet done actions (`FOR UPDATE SKIP LOCKED` in Postgres). */
  claimDue(now: Date): Promise<ClaimedAction[]>;
  /** Null for actions without a task or whose task is gone. */
  loadContext(action: ClaimedAction, now: Date): Promise<ActionContext | null>;
  /** Persist the result and mark the action done, in one transaction. */
  complete(action: ClaimedAction, result: ActionResult): Promise<void>;
}

/** Run every due server timer once. Returns how many actions were handled. */
export async function tick(now: Date, port: TickPort): Promise<number> {
  const due = await port.claimDue(now);
  for (const action of due) {
    const forPerson = personAction(action);
    if (forPerson) {
      await port.complete(action, forPerson);
      continue;
    }
    const ctx = await port.loadContext(action, now);
    await port.complete(action, ctx ? handleAction(action, ctx) : noop);
  }
  return due.length;
}

export interface FiredReport {
  planVersion: number;
  seq: number;
  firedAt: Date;
  /** The level that actually rang; FULL_SCREEN degrades to SOUND without the permission. */
  level: PokePlan["steps"][number]["level"];
  permissionMissing: boolean;
}

export interface FiredResult {
  events: TaskEvent[];
  /** The step to mark fired, if the report is for the current plan. */
  firedSeq: number | null;
  pushes: PushMessage[];
}

/**
 * A device reports a fired step (docs/domain.md, Device execution, step 5). A report for a
 * closed task or an old plan is answered with a cancel so the ring stops on the next sync.
 */
export function reportFired(
  ctx: { task: Task; plan: PokePlan },
  report: FiredReport,
  now: Date,
): FiredResult {
  const { task, plan } = ctx;
  if (report.planVersion !== task.planVersion || !isOpen(task.status)) {
    return {
      events: [],
      firedSeq: null,
      pushes: [
        { to: task.recipientId, hint: "task_cancelled", taskId: task.id, event: "cancelled" },
      ],
    };
  }
  const step = plan.steps.find((s) => s.seq === report.seq);
  if (!step || step.firedAt !== null) return { events: [], firedSeq: null, pushes: [] };
  const events: TaskEvent[] = [
    {
      taskId: task.id,
      actorId: task.recipientId,
      type: "poke_fired",
      payload: {
        seq: step.seq,
        level: report.level,
        planned: step.plannedAt.toISOString(),
        actual: report.firedAt.toISOString(),
      },
      at: now,
    },
  ];
  if (report.permissionMissing) {
    events.push({
      taskId: task.id,
      actorId: task.recipientId,
      type: "permission_missing",
      payload: { seq: step.seq, planned: step.level },
      at: now,
    });
  }
  return { events, firedSeq: step.seq, pushes: [] };
}
