// Core domain types (docs/domain.md). Plain data; persistence maps these to the Drizzle schema.

export type PersonId = string;
export type TaskId = string;

// Server statuses. QUEUED is client-only and never reaches the server.
export type TaskStatus =
  "SCHEDULED" | "AWAITING_APPROVAL" | "REJECTED" | "DONE" | "DROPPED" | "MISSED" | "CANCELLED";

export type FinishMode = "done" | "photo";

/** `by`: done by the deadline. `at`: a moment (`remindAt`); the deadline is the moment plus a grace (ADR 0017). */
export type Timing = "at" | "by";

export type PokeLevel = "SILENT" | "SOUND" | "FULL_SCREEN" | "HUMAN";
export type DeviceLevel = Exclude<PokeLevel, "HUMAN">;

export type EdgeType = "spouse" | "parent" | "child" | "sibling" | "roommate" | "other";
type EdgeStatus = "pending" | "active" | "removed";

/** Local wall-clock times ("HH:MM") in the person's IANA time zone. start === end means none. */
export interface QuietHours {
  start: string;
  end: string;
  timeZone: string;
}

export interface Person {
  id: PersonId;
  phone: string;
  displayName: string;
  quietHours: QuietHours;
  /** "Benim işlerimde tanıdıklarımı kullanabilirsin" */
  useConnectionsForMyTasks: boolean;
  /** "Başkalarının işleri için bana haber verilebilir" */
  notifyMeForOthersTasks: boolean;
  deletedAt: Date | null;
}

export interface Edge {
  fromPersonId: PersonId;
  toPersonId: PersonId;
  type: EdgeType;
  status: EdgeStatus;
  /** "Sibel'in işleri için bana haber verilebilir" (5b): the from-person may be told about the to-person's tasks. */
  notifyMe: boolean;
}

export type CancelReason =
  "sender_cancelled" | "sender_handles" | "account_deleted" | "connection_removed";

export interface Task {
  id: TaskId;
  senderId: PersonId;
  recipientId: PersonId;
  title: string;
  detail: string | null;
  /** When the task becomes missed. For an `at` task the moment plus its grace. */
  deadline: Date;
  timing: Timing;
  /** The moment of an `at` task, null for `by`. No poke comes before it. */
  remindAt: Date | null;
  finishMode: FinishMode;
  rawText: string | null;
  status: TaskStatus;
  late: boolean;
  planVersion: number;
  /** When the status last changed. The missed card expiry counts from here. */
  statusChangedAt: Date;
  /** When the recipient removed the missed card from their list ("Kaldır", NUD-49); null while it shows. */
  removedByRecipientAt: Date | null;
}

export interface PokeStep {
  seq: number;
  plannedAt: Date;
  level: PokeLevel;
  firedAt: Date | null;
}

/** One version of a task's plan. An empty `steps` list means poking is stopped. */
export interface PokePlan {
  taskId: TaskId;
  version: number;
  steps: PokeStep[];
}

export type TaskEventType =
  | "created"
  | "edited"
  | "plan_generated"
  | "poke_fired"
  | "poke_lost"
  | "snoozed"
  | "done"
  | "photo_sent"
  | "approved"
  | "re_requested"
  | "dropped"
  | "missed"
  | "cancelled"
  | "human_notified"
  | "permission_missing";

export interface TaskEvent {
  taskId: TaskId;
  /** Null when the server acts (deadline passed, auto-approve). */
  actorId: PersonId | null;
  type: TaskEventType;
  payload: Record<string, unknown>;
  at: Date;
}

type ScheduledActionKind =
  | "deadline"
  | "approval_timeout"
  | "human_check"
  | "lost_poke_check"
  | "missed_expiry"
  | "permission_request_cooldown"
  /** Deletes a closed task's photos again in case the delete right after closing failed (NUD-25). */
  | "photo_cleanup";

export interface ScheduledAction {
  kind: ScheduledActionKind;
  taskId: TaskId | null;
  dueAt: Date;
  payload: Record<string, unknown>;
}

/**
 * FCM data messages are only hints (docs/architecture.md, Sync model). The device syncs on any of
 * them; `event` says what happened so tests can assert who was told what.
 */
export interface PushMessage {
  to: PersonId;
  hint: "sync" | "plan_changed" | "task_cancelled";
  taskId: TaskId | null;
  event: TaskEventType | "invite_accepted";
}
