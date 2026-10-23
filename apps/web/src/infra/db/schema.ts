// Database schema (docs/domain.md, Entities). drizzle-kit generates SQL from this file into
// supabase/migrations/. Grants, RLS and cron jobs are hand-written SQL in the same folder.
// There is no group, household or role table, and there must never be one (ADR 0010).
import { sql } from "drizzle-orm";
import type { PermissionSummary } from "../../domain/reachability";
import type { RepeatRule } from "../../domain/repeat";
import {
  bigint,
  boolean,
  doublePrecision,
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
export const finishMode = pgEnum("finish_mode", ["done", "photo", "notice"]);
// ADR 0017: `by` is done by the deadline, `at` is a moment (remind_at) and the deadline its grace.
export const timing = pgEnum("timing", ["at", "by"]);

export const contactAction = pgEnum("contact_action", ["call", "message"]);
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
  "delivered",
  "seen",
  "start_set",
  "example_photo_set",
]);
export const pokeLevel = pgEnum("poke_level", ["SILENT", "SOUND", "FULL_SCREEN", "HUMAN"]);
export const scheduledActionKind = pgEnum("scheduled_action_kind", [
  "deadline",
  "approval_timeout",
  "human_check",
  "lost_poke_check",
  "missed_expiry",
  "permission_request_cooldown",
  "photo_cleanup",
  "account_purge",
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
  // The profile picture in the private bucket, `avatars/<person>/<uuid>.jpg`; null for the initial (NUD-72).
  avatarPath: text("avatar_path"),
  // The rolling poke profile the planner reads (ADR 0020): the text, when it was written and how
  // many closed tasks it had seen.
  pokeProfile: text("poke_profile"),
  pokeProfileAt: timestamp("poke_profile_at", { withTimezone: true }),
  pokeProfileTasks: bigint("poke_profile_tasks", { mode: "number" }).notNull().default(0),
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
    // "Sibel'in işleri için bana haber verilebilir" (5b), the from-person's switch for this connection.
    notifyMe: boolean("notify_me").notNull().default(true),
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
    timing: timing("timing").notNull().default("by"),
    // The moment of an `at` task; null for `by`.
    remindAt: timestamp("remind_at", { withTimezone: true }),
    // Minutes before the moment the person must start (NUD-70); 0 for `by`.
    leadMinutes: bigint("lead_minutes", { mode: "number" }).notNull().default(0),
    // The recipient's earliest poke time on a `by` task (NUD-78); null: the plan decides.
    pokeFrom: timestamp("poke_from", { withTimezone: true }),
    // "Kaldır" on the recipient's missed card (NUD-49): hidden from their list from then on.
    removedByRecipientAt: timestamp("removed_by_recipient_at", { withTimezone: true }),
    finishMode: finishMode("finish_mode").notNull(),
    rawText: text("raw_text"),
    // A repeating task's rule and the id of its first occurrence (ADR 0021); null for a one-off.
    repeat: jsonb("repeat").$type<RepeatRule>(),
    seriesId: uuid("series_id"),
    // Whom the recipient calls or messages from the poke (NUD-128); both null for most tasks.
    contactPersonId: uuid("contact_person_id").references(() => people.id),
    contactAction: contactAction("contact_action"),
    // The sender's example photo in the private bucket (NUD-138); null without one or once closed.
    examplePhotoPath: text("example_photo_path"),
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

// Messages from Nuddle (ADR 0025), written by `mise run announce` from `announcements/<slug>.md`.
// Sending the same slug again updates the text.
export const announcements = pgTable("announcements", {
  id: uuid("id").primaryKey().defaultRandom(),
  slug: text("slug").notNull().unique(),
  title: text("title").notNull(),
  summary: text("summary").notNull(),
  // CommonMark; images by root-relative path under apps/web/public/announcements/<slug>/.
  body: text("body").notNull(),
  buttonLabel: text("button_label"),
  // An app route or an https URL.
  buttonTarget: text("button_target"),
  createdAt: createdAt(),
  updatedAt: updatedAt(),
});

// Who got which announcement. The sync feed reads these rows; an update of the text bumps
// change_seq of every row so phones get the new text.
export const announcementRecipients = pgTable(
  "announcement_recipients",
  {
    announcementId: uuid("announcement_id")
      .notNull()
      .references(() => announcements.id),
    personId: uuid("person_id")
      .notNull()
      .references(() => people.id),
    createdAt: createdAt(),
    changeSeq: changeSeq(),
  },
  (t) => [
    primaryKey({ columns: [t.announcementId, t.personId] }),
    index("announcement_recipients_person_idx").on(t.personId),
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
    // Firebase Installation ID; FCM targets it with `fids` (ADR 0015).
    fid: text("fid").notNull().unique(),
    platform: devicePlatform("platform").notNull(),
    appVersion: text("app_version").notNull(),
    // "Samsung SM-S911B" as the app reports it, shown in Cihazlarım (ADR 0027); null before it does.
    model: text("model"),
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

export const sessionSeenVia = pgEnum("session_seen_via", ["verify", "refresh", "device", "sync"]);

// Which device holds each Supabase session (ADR 0027): ids, the app version and times only, never
// IPs, user agents or tokens. No foreign keys: a session starts before `register` creates the
// person, and log out deletes the device row while the session row stays.
export const sessionDevices = pgTable(
  "session_devices",
  {
    // The access token's `session_id` claim.
    sessionId: uuid("session_id").primaryKey(),
    personId: uuid("person_id").notNull(),
    deviceId: uuid("device_id"),
    appVersion: text("app_version"),
    createdAt: createdAt(),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull().defaultNow(),
    lastSeenVia: sessionSeenVia("last_seen_via").notNull(),
    // Ended by sign_out_others; the middleware refuses its access tokens.
    revokedAt: timestamp("revoked_at", { withTimezone: true }),
  },
  (t) => [index("session_devices_person_idx").on(t.personId)],
);

export const foreignDeviceVia = pgEnum("foreign_device_via", [
  "verify",
  "refresh",
  "sync",
  "report_fired",
  "log_out",
]);

// A request named a device that is not the caller's (ADR 0027): ids only. `ownerId` is null when
// no device has that id.
export const foreignDeviceUses = pgTable(
  "foreign_device_uses",
  {
    id: bigint("id", { mode: "number" }).primaryKey().generatedAlwaysAsIdentity(),
    personId: uuid("person_id").notNull(),
    sessionId: uuid("session_id"),
    deviceId: uuid("device_id").notNull(),
    ownerId: uuid("owner_id"),
    via: foreignDeviceVia("via").notNull(),
    at: timestamp("at", { withTimezone: true }).notNull(),
  },
  (t) => [index("foreign_device_uses_person_idx").on(t.personId)],
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

// Wrong sign-in codes per phone and the 15 minute wait after the last one (screen 6c, ADR 0011).
export const otpAttempts = pgTable("otp_attempts", {
  phone: text("phone").primaryKey(),
  failures: bigint("failures", { mode: "number" }).notNull().default(0),
  lockedUntil: timestamp("locked_until", { withTimezone: true }),
  updatedAt: updatedAt(),
});

// Every LLM call with what the model got and said, and what came of it (ADR 0019): golden data
// for evals and the only record of why a plan fell back. Only the API reads it.
export const llmCallKind = pgEnum("llm_call_kind", ["extraction", "plan", "profile"]);

export const llmCalls = pgTable(
  "llm_calls",
  {
    id: uuid("id").primaryKey(),
    kind: llmCallKind("kind").notNull(),
    // The writer for an extraction, the recipient for a plan.
    personId: uuid("person_id")
      .notNull()
      .references(() => people.id),
    // Null for an extraction that created no task (a question, a reply, an error).
    taskId: uuid("task_id").references(() => tasks.id),
    // The write_task key, so an extraction that asked links to the task the preview created.
    writeKey: uuid("write_key"),
    // The plan version this call was for, and the one it became when adopted.
    planVersion: bigint("plan_version", { mode: "number" }),
    adoptedVersion: bigint("adopted_version", { mode: "number" }),
    // The structured input: the extraction input or the PlannerInput.
    input: jsonb("input").$type<unknown>().notNull(),
    model: text("model"),
    system: text("system"),
    prompt: text("prompt"),
    // What the model answered, parsed when it was JSON.
    output: jsonb("output").$type<unknown>(),
    // create, ask, chat; adopted, rejected, stale; failed.
    outcome: text("outcome").notNull(),
    error: text("error"),
    latencyMs: bigint("latency_ms", { mode: "number" }).notNull(),
    // What the call used, as the provider reported it (ADR 0024); null when it did not.
    promptTokens: bigint("prompt_tokens", { mode: "number" }),
    completionTokens: bigint("completion_tokens", { mode: "number" }),
    costUsd: doublePrecision("cost_usd"),
    createdAt: createdAt(),
  },
  (t) => [index("llm_calls_task_idx").on(t.taskId), index("llm_calls_person_idx").on(t.personId)],
);

// Every sign-in code the Send SMS hook handed to the provider, for the daily caps per phone and in
// total (ADR 0024). Rows older than the window are deleted when a new code is sent.
export const smsSends = pgTable(
  "sms_sends",
  {
    id: uuid("id").primaryKey(),
    phone: text("phone").notNull(),
    sentAt: timestamp("sent_at", { withTimezone: true }).notNull(),
  },
  (t) => [index("sms_sends_sent_at_idx").on(t.sentAt), index("sms_sends_phone_idx").on(t.phone)],
);
