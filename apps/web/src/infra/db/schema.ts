// Database schema (docs/domain.md, Entities). drizzle-kit generates SQL from this file into
// supabase/migrations/. Grants, RLS and cron jobs are hand-written SQL in the same folder.
// There is no group, household or role table, and there must never be one (ADR 0010).
import { sql } from "drizzle-orm";
import type { PermissionSummary } from "../../domain/reachability";
import {
  bigint,
  boolean,
  index,
  jsonb,
  pgEnum,
  pgSequence,
  pgTable,
  primaryKey,
  text,
  time,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core";

const createdAt = () => timestamp("created_at", { withTimezone: true }).notNull().defaultNow();
const updatedAt = () => timestamp("updated_at", { withTimezone: true }).notNull().defaultNow();

// One sequence for every row the sync feed exposes (ADR 0006). Inserts take the next value by
// default; every update sets `change_seq = nextval('sync_seq')`. `GET /api/v1/sync?cursor=`
// returns rows with a larger value. Writers serialize on an advisory lock so values become
// visible in order (src/infra/db/store.ts).
export const syncSeq = pgSequence("sync_seq");
const changeSeq = () =>
  bigint("change_seq", { mode: "number" })
    .notNull()
    .default(sql`nextval('sync_seq')`);

export const edgeType = pgEnum("edge_type", [
  "spouse",
  "parent",
  "child",
  "sibling",
  "roommate",
  "other",
]);
export const edgeStatus = pgEnum("edge_status", ["pending", "active", "removed"]);
export const inviteStatus = pgEnum("invite_status", [
  "open",
  "accepted",
  "declined",
  "revoked",
  "expired",
]);
export const finishMode = pgEnum("finish_mode", ["done", "photo"]);
// Server statuses only. QUEUED is client-only and never stored here.
export const taskStatus = pgEnum("task_status", [
  "SCHEDULED",
  "AWAITING_APPROVAL",
  "REJECTED",
  "DONE",
  "DROPPED",
  "MISSED",
  "CANCELLED",
]);
// Proposed vocabulary from docs/domain.md (Task events).
export const taskEventType = pgEnum("task_event_type", [
  "created",
  "edited",
  "plan_generated",
  "poke_fired",
  "poke_lost",
  "snoozed",
  "done",
  "photo_sent",
  "approved",
  "re_requested",
  "dropped",
  "missed",
  "cancelled",
  "human_notified",
  "permission_missing",
]);
export const pokeLevel = pgEnum("poke_level", ["SILENT", "SOUND", "FULL_SCREEN", "HUMAN"]);
export const scheduledActionKind = pgEnum("scheduled_action_kind", [
  "deadline",
  "approval_timeout",
  "human_check",
  "lost_poke_check",
  "missed_expiry",
  "permission_request_cooldown",
]);
export const devicePlatform = pgEnum("device_platform", ["android", "ios"]);

export const people = pgTable("people", {
  id: uuid("id").primaryKey().defaultRandom(),
  // E.164, the only identity (ADR 0011).
  phone: text("phone").notNull().unique(),
  displayName: text("display_name").notNull(),
  quietHoursStart: time("quiet_hours_start").notNull().default("23:00"),
  quietHoursEnd: time("quiet_hours_end").notNull().default("07:00"),
  timeZone: text("time_zone").notNull().default("Europe/Istanbul"),
  // The two human channel switches (docs/product.md, Human channel), both default on.
  // "Benim işlerimde tanıdıklarımı kullanabilirsin"
  useConnectionsForMyTasks: boolean("use_connections_for_my_tasks").notNull().default(true),
  // "Başkalarının işleri için bana haber verilebilir"
  notifyMeForOthersTasks: boolean("notify_me_for_others_tasks").notNull().default(true),
  createdAt: createdAt(),
  deletedAt: timestamp("deleted_at", { withTimezone: true }),
});

// A 1:1 connection is two directed rows created together; each side picks its own type and the
// weights are per direction (ADR 0010).
export const edges = pgTable(
  "edges",
  {
    fromPersonId: uuid("from_person_id")
      .notNull()
      .references(() => people.id),
    toPersonId: uuid("to_person_id")
      .notNull()
      .references(() => people.id),
    type: edgeType("type").notNull(),
    status: edgeStatus("status").notNull(),
    weights: jsonb("weights").notNull().default({}),
    createdAt: createdAt(),
    updatedAt: updatedAt(),
    changeSeq: changeSeq(),
  },
  (t) => [
    primaryKey({ columns: [t.fromPersonId, t.toPersonId] }),
    index("edges_to_person_idx").on(t.toPersonId),
  ],
);

export const invites = pgTable(
  "invites",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    inviterId: uuid("inviter_id")
      .notNull()
      .references(() => people.id),
    // Bound to the number so a lost link still reaches the invitee (ADR 0011).
    inviteePhone: text("invitee_phone").notNull(),
    relationship: edgeType("relationship").notNull(),
    prefilledName: text("prefilled_name").notNull(),
    code: text("code").notNull().unique(),
    // Declined is stored but never shown to the inviter.
    status: inviteStatus("status").notNull().default("open"),
    createdAt: createdAt(),
    updatedAt: updatedAt(),
    changeSeq: changeSeq(),
  },
  (t) => [
    index("invites_inviter_idx").on(t.inviterId),
    index("invites_invitee_phone_idx").on(t.inviteePhone),
  ],
);

export const tasks = pgTable(
  "tasks",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    senderId: uuid("sender_id")
      .notNull()
      .references(() => people.id),
    // Equals sender_id for a task to self.
    recipientId: uuid("recipient_id")
      .notNull()
      .references(() => people.id),
    title: text("title").notNull(),
    detail: text("detail"),
    deadline: timestamp("deadline", { withTimezone: true }).notNull(),
    finishMode: finishMode("finish_mode").notNull(),
    rawText: text("raw_text"),
    status: taskStatus("status").notNull().default("SCHEDULED"),
    // TODO(open-questions#9): "Sonradan yaptım" after MISSED closes as DONE with late = true.
    late: boolean("late").notNull().default(false),
    planVersion: bigint("plan_version", { mode: "number" }).notNull().default(0),
    createdAt: createdAt(),
    // When the status last changed (the domain's `Task.statusChangedAt`); written on every
    // status change and only then. The missed card expiry counts from here.
    updatedAt: updatedAt(),
    changeSeq: changeSeq(),
  },
  (t) => [index("tasks_sender_idx").on(t.senderId), index("tasks_recipient_idx").on(t.recipientId)],
);

// Append-only. The id is the sync cursor (ADR 0006), so it is a monotonic sequence.
export const taskEvents = pgTable(
  "task_events",
  {
    id: bigint("id", { mode: "bigint" }).primaryKey().generatedAlwaysAsIdentity(),
    taskId: uuid("task_id")
      .notNull()
      .references(() => tasks.id),
    // Null when the server acts (deadline passed, auto-approve).
    actorId: uuid("actor_id").references(() => people.id),
    type: taskEventType("type").notNull(),
    payload: jsonb("payload").$type<Record<string, unknown>>().notNull().default({}),
    createdAt: createdAt(),
    changeSeq: changeSeq(),
  },
  (t) => [index("task_events_task_idx").on(t.taskId)],
);

// The plan. Never exposed to clients except the recipient's device.
export const pokeSteps = pgTable(
  "poke_steps",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    taskId: uuid("task_id")
      .notNull()
      .references(() => tasks.id),
    planVersion: bigint("plan_version", { mode: "number" }).notNull(),
    seq: bigint("seq", { mode: "number" }).notNull(),
    plannedAt: timestamp("planned_at", { withTimezone: true }).notNull(),
    level: pokeLevel("level").notNull(),
    firedAt: timestamp("fired_at", { withTimezone: true }),
    createdAt: createdAt(),
  },
  (t) => [uniqueIndex("poke_steps_task_plan_seq_idx").on(t.taskId, t.planVersion, t.seq)],
);

// Server timers, claimed by tick(now) with FOR UPDATE SKIP LOCKED (ADR 0007).
export const scheduledActions = pgTable(
  "scheduled_actions",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    kind: scheduledActionKind("kind").notNull(),
    taskId: uuid("task_id").references(() => tasks.id),
    dueAt: timestamp("due_at", { withTimezone: true }).notNull(),
    payload: jsonb("payload").$type<Record<string, unknown>>().notNull().default({}),
    doneAt: timestamp("done_at", { withTimezone: true }),
    // Lease set by tick's claim (FOR UPDATE SKIP LOCKED); another tick skips the row until it
    // runs out, so a crashed run is retried.
    claimedUntil: timestamp("claimed_until", { withTimezone: true }),
    createdAt: createdAt(),
  },
  (t) => [
    index("scheduled_actions_due_idx")
      .on(t.dueAt)
      .where(sql`${t.doneAt} is null`),
  ],
);

// Source of reachability (docs/domain.md, Reachability).
export const devices = pgTable(
  "devices",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    personId: uuid("person_id")
      .notNull()
      .references(() => people.id),
    fcmToken: text("fcm_token").notNull().unique(),
    platform: devicePlatform("platform").notNull(),
    appVersion: text("app_version").notNull(),
    // Notifications, exact alarm, full screen and channel states as reported by the device.
    permissions: jsonb("permissions")
      .$type<PermissionSummary>()
      .notNull()
      .default(sql`'{}'::jsonb`),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull().defaultNow(),
    createdAt: createdAt(),
  },
  (t) => [index("devices_person_idx").on(t.personId)],
);

// Replayed commands return the stored result (ADR 0006).
export const idempotencyKeys = pgTable(
  "idempotency_keys",
  {
    key: uuid("key").notNull(),
    callerId: uuid("caller_id")
      .notNull()
      .references(() => people.id),
    command: text("command").notNull(),
    result: jsonb("result").$type<unknown>().notNull(),
    createdAt: createdAt(),
  },
  (t) => [primaryKey({ columns: [t.callerId, t.key] })],
);
