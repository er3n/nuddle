import type { RepeatRule } from "./repeat";

// Core domain types (docs/domain.md). Plain data; persistence maps these to the Drizzle schema.

export type PersonId = string;
export type TaskId = string;

// Server statuses. QUEUED is client-only and never reaches the server.
export type TaskStatus =
  "SCHEDULED" | "AWAITING_APPROVAL" | "REJECTED" | "DONE" | "DROPPED" | "MISSED" | "CANCELLED";

/** `notice`: a one-time message (NUD-79): delivered once, "Tamam" closes it, the deadline closes it too. */
export type FinishMode = "done" | "photo" | "notice";

/** `by`: done by the deadline. `at`: a moment (`remindAt`); the deadline is the moment plus a grace (ADR 0017). */
export type Timing = "at" | "by";

/**
 * The person a task is about calling or messaging ("annemi ara", NUD-128): the recipient's phone
 * offers Ara or Mesaj at on the full screen poke with that connection's number.
 */
export interface TaskContact {
  personId: PersonId;
  action: "call" | "message";
}

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
  /** The profile picture's path in the private bucket; null shows the initial (NUD-72). */
  avatarPath: string | null;
  /** How this person responds to pokes, written by the LLM (ADR 0020); null until 10 tasks closed. */
  pokeProfile: PokeProfile | null;
  deletedAt: Date | null;
}

export interface PokeProfile {
  text: string;
  writtenAt: Date;
  /** Closed tasks the profile had seen when it was written. */
  tasksSeen: number;
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
  | "sender_cancelled"
  | "sender_handles"
  | "account_deleted"
  | "connection_removed"
  /** Kime changed: the same task now goes to someone else (NUD-114). */
  | "reassigned";

export interface Task {
  id: TaskId;
  senderId: PersonId;
  recipientId: PersonId;
  title: string;
  detail: string | null;
  /** When the task becomes missed. For an `at` task the moment plus its grace. */
  deadline: Date;
  timing: Timing;
  /** The moment of an `at` task, null for `by`. */
  remindAt: Date | null;
  /**
   * How many minutes before the moment the person must start (getting ready, travelling), so the
   * first poke of an `at` task comes that much earlier (NUD-70). 0 for `by` and for things done on
   * the spot.
   */
  leadMinutes: number;
  /**
   * The earliest the pokes of a `by` task may begin, set by the recipient ("Saat 09:00 olunca
   * hatırlatırım" moved; NUD-78). Null: the plan decides. For an `at` task the lead holds this.
   */
  pokeFrom: Date | null;
  finishMode: FinishMode;
  rawText: string | null;
  /** The rule of a repeating task (ADR 0021); null for a one-off. Every occurrence carries it. */
  repeat: RepeatRule | null;
  /** The first occurrence's id, shared by every occurrence of the series; null for a one-off. */
  seriesId: TaskId | null;
  /** Whom to call or message from the poke (NUD-128); null for most tasks. */
  contact: TaskContact | null;
  /**
   * The sender's example photo in the private bucket (NUD-138, docs/domain.md, Example photo):
   * shows what to do, never proof. Cleared when the task closes and its folder is deleted.
   */
  examplePhotoPath: string | null;
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
  | "permission_missing"
  | "delivered"
  | "seen"
  | "start_set"
  | "example_photo_set";

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
  | "photo_cleanup"
  /**
   * Deletes what is left of a deleted account a week later: every task with the person, its
   * events and plans, the connections and the person row (NUD-89). No task; `payload.personId`.
   */
  | "account_purge";

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
  event: TaskEventType | "invite_accepted" | "announcement" | "signed_out_others";
}
