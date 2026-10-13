// Postgres persistence for the API and tick (ADR 0006, 0007): row mapping to domain types and the
// queries the commands, sync and timers need. Orchestration lives in src/api/services.ts.
//
// Every write that touches sync rows runs in `inWriteTx`, which takes one advisory lock. That
// serializes writers so `change_seq` values commit in order and a sync cursor never skips a row
// committed late. Fine at MVP scale; revisit (e.g. per-person locks) when writes contend.
import { and, asc, eq, gt, inArray, isNull, lte, ne, notInArray, or, sql } from "drizzle-orm";
import type { PostgresJsDatabase } from "drizzle-orm/postgres-js";
import type { Invite, InviteStatus } from "../../domain/connections";
import { noAttempts, type OtpAttempts } from "../../domain/otp-attempts";
import type { Device, PermissionSummary } from "../../domain/reachability";
import type { ClaimedAction } from "../../domain/tick";
import type { Outcome } from "../../domain/task-machine";
import type { Edge, Person, PersonId, PokePlan, Task, TaskEvent, TaskId } from "../../domain/types";
import {
  devices,
  edges,
  idempotencyKeys,
  invites,
  otpAttempts,
  people,
  pokeSteps,
  scheduledActions,
  taskEvents,
  tasks,
} from "./schema";
import type * as schema from "./schema";

export type Db = PostgresJsDatabase<typeof schema>;
export type Tx = Parameters<Parameters<Db["transaction"]>[0]>[0];
type Executor = Db | Tx;

const nextSeq = sql`nextval('sync_seq')`;

/** Health check: whether `select 1` returns within `timeoutMs`. Never throws. */
export async function databaseAnswers(db: Db, timeoutMs: number): Promise<boolean> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<boolean>((resolve) => {
    timer = setTimeout(() => {
      resolve(false);
    }, timeoutMs);
  });
  const query = db.execute(sql`select 1`).then(
    () => true,
    () => false,
  );
  try {
    return await Promise.race([query, timeout]);
  } finally {
    clearTimeout(timer);
  }
}

/** A write transaction holding the sync write lock (see the file comment). */
export function inWriteTx<T>(db: Db, fn: (tx: Tx) => Promise<T>): Promise<T> {
  return db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(4242001)`);
    return fn(tx);
  });
}

/** A read transaction that waits for in-flight writers, so its cursor is safe to hand out. */
export function inSyncReadTx<T>(db: Db, fn: (tx: Tx) => Promise<T>): Promise<T> {
  return db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock_shared(4242001)`);
    return fn(tx);
  });
}

// ---- mapping --------------------------------------------------------------------------------

type PersonRow = typeof people.$inferSelect;
type TaskRow = typeof tasks.$inferSelect;
type DeviceRow = typeof devices.$inferSelect;
type InviteRow = typeof invites.$inferSelect;
type EventRow = typeof taskEvents.$inferSelect;
type EdgeRow = typeof edges.$inferSelect;

export function toPerson(r: PersonRow): Person {
  return {
    id: r.id,
    phone: r.phone,
    displayName: r.displayName,
    // Postgres `time` reads as "HH:MM:SS".
    quietHours: {
      start: r.quietHoursStart.slice(0, 5),
      end: r.quietHoursEnd.slice(0, 5),
      timeZone: r.timeZone,
    },
    useConnectionsForMyTasks: r.useConnectionsForMyTasks,
    notifyMeForOthersTasks: r.notifyMeForOthersTasks,
    deletedAt: r.deletedAt,
  };
}

function toEdge(r: EdgeRow): Edge {
  return {
    fromPersonId: r.fromPersonId,
    toPersonId: r.toPersonId,
    type: r.type,
    status: r.status,
    notifyMe: r.notifyMe,
  };
}

export function toTask(r: TaskRow): Task {
  return {
    id: r.id,
    senderId: r.senderId,
    recipientId: r.recipientId,
    title: r.title,
    detail: r.detail,
    deadline: r.deadline,
    timing: r.timing,
    remindAt: r.remindAt,
    finishMode: r.finishMode,
    rawText: r.rawText,
    status: r.status,
    late: r.late,
    planVersion: r.planVersion,
    // Decided in NUD-8: tasks.updated_at is the status change time.
    statusChangedAt: r.updatedAt,
  };
}

function toDevice(r: DeviceRow): Device {
  return {
    id: r.id,
    personId: r.personId,
    fid: r.fid,
    permissions: r.permissions,
    lastSeenAt: r.lastSeenAt,
  };
}

function toInvite(r: InviteRow): Invite {
  return {
    id: r.id,
    inviterId: r.inviterId,
    inviteePhone: r.inviteePhone,
    relationship: r.relationship,
    prefilledName: r.prefilledName,
    code: r.code,
    status: r.status,
  };
}

function toEvent(r: EventRow): TaskEvent {
  return {
    taskId: r.taskId,
    actorId: r.actorId,
    type: r.type,
    payload: r.payload,
    at: r.createdAt,
  };
}

// ---- people and connections -----------------------------------------------------------------

export async function findPerson(ex: Executor, id: PersonId): Promise<Person | null> {
  const [row] = await ex.select().from(people).where(eq(people.id, id));
  return row ? toPerson(row) : null;
}

export async function upsertPerson(
  tx: Tx,
  p: { id: PersonId; phone: string; displayName: string; timeZone: string; now: Date },
): Promise<Person> {
  const [row] = await tx
    .insert(people)
    .values({
      id: p.id,
      phone: p.phone,
      displayName: p.displayName,
      timeZone: p.timeZone,
      createdAt: p.now,
    })
    .onConflictDoUpdate({
      target: people.id,
      set: { displayName: p.displayName, timeZone: p.timeZone },
    })
    .returning();
  if (!row) throw new Error("person upsert returned nothing");
  // Connections show the name: bump the edges pointing at this person.
  await touchEdgesTo(tx, p.id);
  return toPerson(row);
}

/** Quiet hours and the two human channel switches (5a, 7g). */
export async function updatePersonSettings(
  tx: Tx,
  id: PersonId,
  patch: {
    quietHours?: { start: string; end: string };
    useConnectionsForMyTasks?: boolean;
    notifyMeForOthersTasks?: boolean;
  },
): Promise<Person> {
  const [row] = await tx
    .update(people)
    .set({
      ...(patch.quietHours === undefined
        ? {}
        : { quietHoursStart: patch.quietHours.start, quietHoursEnd: patch.quietHours.end }),
      ...(patch.useConnectionsForMyTasks === undefined
        ? {}
        : { useConnectionsForMyTasks: patch.useConnectionsForMyTasks }),
      ...(patch.notifyMeForOthersTasks === undefined
        ? {}
        : { notifyMeForOthersTasks: patch.notifyMeForOthersTasks }),
    })
    .where(eq(people.id, id))
    .returning();
  if (!row) throw new Error(`no person ${id}`);
  return toPerson(row);
}

export async function markPersonDeleted(tx: Tx, person: Person): Promise<void> {
  await tx.update(people).set({ deletedAt: person.deletedAt }).where(eq(people.id, person.id));
}

/** The person's active outgoing connections: who they can write to, with names. */
export async function activeConnections(
  ex: Executor,
  personId: PersonId,
): Promise<{ edge: Edge; person: Person }[]> {
  const rows = await ex
    .select()
    .from(edges)
    .innerJoin(people, eq(people.id, edges.toPersonId))
    .where(and(eq(edges.fromPersonId, personId), eq(edges.status, "active")))
    .orderBy(asc(edges.changeSeq));
  return rows.map((r) => ({
    edge: toEdge(r.edges),
    person: toPerson(r.people),
  }));
}

export async function findEdge(ex: Executor, from: PersonId, to: PersonId): Promise<Edge | null> {
  const [row] = await ex
    .select()
    .from(edges)
    .where(and(eq(edges.fromPersonId, from), eq(edges.toPersonId, to)));
  return row ? toEdge(row) : null;
}

export async function edgesTouching(ex: Executor, personId: PersonId): Promise<Edge[]> {
  const rows = await ex
    .select()
    .from(edges)
    .where(or(eq(edges.fromPersonId, personId), eq(edges.toPersonId, personId)))
    .orderBy(asc(edges.changeSeq));
  return rows.map(toEdge);
}

export async function upsertEdges(tx: Tx, list: readonly Edge[], now: Date): Promise<void> {
  for (const e of list) {
    await tx
      .insert(edges)
      .values({ ...e, createdAt: now, updatedAt: now })
      .onConflictDoUpdate({
        target: [edges.fromPersonId, edges.toPersonId],
        set: {
          type: e.type,
          status: e.status,
          notifyMe: e.notifyMe,
          // "3 aydır bağlısınız" (5b) counts from the latest time the connection became active.
          createdAt: sql`case when ${edges.status} <> 'active' and excluded.status = 'active' then excluded.created_at else ${edges.createdAt} end`,
          updatedAt: now,
          changeSeq: nextSeq,
        },
      });
  }
}

/** Other people's connection rows for this person change (name, reachability). */
export async function touchEdgesTo(tx: Tx, personId: PersonId): Promise<void> {
  await tx.update(edges).set({ changeSeq: nextSeq }).where(eq(edges.toPersonId, personId));
}

// ---- invites --------------------------------------------------------------------------------

export async function insertInvite(tx: Tx, invite: Invite, now: Date): Promise<void> {
  await tx.insert(invites).values({ ...invite, createdAt: now, updatedAt: now });
}

export async function findInviteByCode(tx: Tx, code: string): Promise<Invite | null> {
  const [row] = await tx.select().from(invites).where(eq(invites.code, code)).for("update");
  return row ? toInvite(row) : null;
}

/** The open invite with this code and its inviter's name, without a lock (read only, ADR 0016). */
export async function openInvitePreview(
  ex: Executor,
  code: string,
): Promise<{ inviterName: string; prefilledName: string } | null> {
  const [row] = await ex
    .select({ inviterName: people.displayName, prefilledName: invites.prefilledName })
    .from(invites)
    .innerJoin(people, eq(people.id, invites.inviterId))
    .where(and(eq(invites.code, code), eq(invites.status, "open")));
  return row ?? null;
}

export async function findInviteById(tx: Tx, id: string): Promise<Invite | null> {
  const [row] = await tx.select().from(invites).where(eq(invites.id, id)).for("update");
  return row ? toInvite(row) : null;
}

/**
 * `visibleChange` false keeps the sync cursor untouched, so the inviter's feed does not even
 * re-send the row when an invite is declined (declining is never shown to the inviter).
 */
export async function setInviteStatus(
  tx: Tx,
  id: string,
  status: InviteStatus,
  now: Date,
  visibleChange: boolean,
): Promise<void> {
  await tx
    .update(invites)
    .set(
      visibleChange ? { status, updatedAt: now, changeSeq: nextSeq } : { status, updatedAt: now },
    )
    .where(eq(invites.id, id));
}

// ---- devices --------------------------------------------------------------------------------

export async function devicesOf(ex: Executor, personId: PersonId): Promise<Device[]> {
  const rows = await ex.select().from(devices).where(eq(devices.personId, personId));
  return rows.map(toDevice);
}

export async function upsertDevice(
  tx: Tx,
  d: {
    id: string | null;
    personId: PersonId;
    fid: string;
    platform: "android" | "ios";
    appVersion: string;
    permissions: PermissionSummary;
    now: Date;
  },
): Promise<string> {
  const values = {
    personId: d.personId,
    fid: d.fid,
    platform: d.platform,
    appVersion: d.appVersion,
    permissions: d.permissions,
    lastSeenAt: d.now,
  };
  if (d.id !== null) {
    // A known device whose FID changed. Another row holding the new FID is stale.
    await tx.delete(devices).where(and(eq(devices.fid, d.fid), ne(devices.id, d.id)));
    const [row] = await tx
      .update(devices)
      .set(values)
      .where(and(eq(devices.id, d.id), eq(devices.personId, d.personId)))
      .returning({ id: devices.id });
    if (row) return row.id;
  }
  // The FID identifies the install; a phone that changed hands moves to the new person.
  const [row] = await tx
    .insert(devices)
    .values({ ...values, createdAt: d.now })
    .onConflictDoUpdate({ target: devices.fid, set: values })
    .returning({ id: devices.id });
  if (!row) throw new Error("device upsert returned nothing");
  return row.id;
}

/** Logout deletes the row (decided in NUD-8); `fid` stays NOT NULL. */
export async function deleteDevice(tx: Tx, personId: PersonId, deviceId: string): Promise<void> {
  await tx.delete(devices).where(and(eq(devices.id, deviceId), eq(devices.personId, personId)));
}

export async function deleteDevicesOf(tx: Tx, personId: PersonId): Promise<void> {
  await tx.delete(devices).where(eq(devices.personId, personId));
}

/** The device was seen online. Not part of the sync feed, so no write lock is needed. */
export async function touchDevice(
  ex: Executor,
  personId: PersonId,
  deviceId: string,
  now: Date,
): Promise<void> {
  await ex
    .update(devices)
    .set({ lastSeenAt: now })
    .where(and(eq(devices.id, deviceId), eq(devices.personId, personId)));
}

export async function fidsOf(ex: Executor, personId: PersonId): Promise<string[]> {
  const rows = await ex
    .select({ fid: devices.fid })
    .from(devices)
    .where(eq(devices.personId, personId));
  return rows.map((r) => r.fid);
}

// ---- tasks, events, plans, timers -------------------------------------------------------------

export async function findTask(tx: Executor, id: TaskId, lock = false): Promise<Task | null> {
  const q = tx.select().from(tasks).where(eq(tasks.id, id));
  const [row] = lock ? await q.for("update") : await q;
  return row ? toTask(row) : null;
}

/** Tasks the person sends or receives that are not closed yet (account deletion). */
export async function unclosedTasksOf(tx: Tx, personId: PersonId): Promise<Task[]> {
  const rows = await tx
    .select()
    .from(tasks)
    .where(
      and(
        or(eq(tasks.senderId, personId), eq(tasks.recipientId, personId)),
        notInArray(tasks.status, ["DONE", "CANCELLED"]),
      ),
    )
    .orderBy(asc(tasks.changeSeq))
    .for("update");
  return rows.map(toTask);
}

/** Tasks between the two people, either direction, that are not closed yet (5b, remove). */
export async function unclosedTasksBetween(tx: Tx, a: PersonId, b: PersonId): Promise<Task[]> {
  const rows = await tx
    .select()
    .from(tasks)
    .where(
      and(
        or(
          and(eq(tasks.senderId, a), eq(tasks.recipientId, b)),
          and(eq(tasks.senderId, b), eq(tasks.recipientId, a)),
        ),
        notInArray(tasks.status, ["DONE", "CANCELLED"]),
      ),
    )
    .orderBy(asc(tasks.changeSeq))
    .for("update");
  return rows.map(toTask);
}

/** Tasks still being poked whose plan depends on a setting: by recipient, sender, or both. */
export async function pokedTasks(
  tx: Tx,
  by: { recipientId?: PersonId; senderId?: PersonId },
): Promise<Task[]> {
  const rows = await tx
    .select()
    .from(tasks)
    .where(
      and(
        by.recipientId === undefined ? undefined : eq(tasks.recipientId, by.recipientId),
        by.senderId === undefined ? undefined : eq(tasks.senderId, by.senderId),
        inArray(tasks.status, ["SCHEDULED", "REJECTED"]),
      ),
    )
    .orderBy(asc(tasks.changeSeq))
    .for("update");
  return rows.map(toTask);
}

export async function eventsOf(ex: Executor, taskId: TaskId): Promise<TaskEvent[]> {
  const rows = await ex
    .select()
    .from(taskEvents)
    .where(eq(taskEvents.taskId, taskId))
    .orderBy(asc(taskEvents.id));
  return rows.map(toEvent);
}

export async function currentPlan(ex: Executor, task: Task): Promise<PokePlan> {
  const rows = await ex
    .select()
    .from(pokeSteps)
    .where(and(eq(pokeSteps.taskId, task.id), eq(pokeSteps.planVersion, task.planVersion)))
    .orderBy(asc(pokeSteps.seq));
  return {
    taskId: task.id,
    version: task.planVersion,
    steps: rows.map((r) => ({
      seq: r.seq,
      plannedAt: r.plannedAt,
      level: r.level,
      firedAt: r.firedAt,
    })),
  };
}

export async function insertEvents(tx: Tx, events: readonly TaskEvent[]): Promise<void> {
  if (events.length === 0) return;
  await tx.insert(taskEvents).values(
    events.map((e) => ({
      taskId: e.taskId,
      actorId: e.actorId,
      type: e.type,
      payload: e.payload,
      createdAt: e.at,
    })),
  );
}

/** Persist a transition: task, events, the new plan version and its timers, in the caller's tx. */
export async function persistOutcome(tx: Tx, o: Outcome, now: Date): Promise<void> {
  const t = o.task;
  await tx
    .insert(tasks)
    .values({
      id: t.id,
      senderId: t.senderId,
      recipientId: t.recipientId,
      title: t.title,
      detail: t.detail,
      deadline: t.deadline,
      timing: t.timing,
      remindAt: t.remindAt,
      finishMode: t.finishMode,
      rawText: t.rawText,
      status: t.status,
      late: t.late,
      planVersion: t.planVersion,
      createdAt: now,
      updatedAt: t.statusChangedAt,
    })
    .onConflictDoUpdate({
      target: tasks.id,
      set: {
        title: t.title,
        detail: t.detail,
        deadline: t.deadline,
        remindAt: t.remindAt,
        finishMode: t.finishMode,
        status: t.status,
        late: t.late,
        planVersion: t.planVersion,
        updatedAt: t.statusChangedAt,
        changeSeq: nextSeq,
      },
    });
  await insertEvents(tx, o.events);
  if (o.plan.steps.length > 0) {
    await tx.insert(pokeSteps).values(
      o.plan.steps.map((s) => ({
        taskId: t.id,
        planVersion: o.plan.version,
        seq: s.seq,
        plannedAt: s.plannedAt,
        level: s.level,
        firedAt: s.firedAt,
        createdAt: now,
      })),
    );
  }
  // Pending timers of the task are cancelled before the new ones are added.
  await tx
    .update(scheduledActions)
    .set({ doneAt: now })
    .where(and(eq(scheduledActions.taskId, t.id), isNull(scheduledActions.doneAt)));
  if (o.scheduledActions.length > 0) {
    await tx.insert(scheduledActions).values(
      o.scheduledActions.map((a) => ({
        kind: a.kind,
        taskId: a.taskId,
        dueAt: a.dueAt,
        payload: a.payload,
        createdAt: now,
      })),
    );
  }
}

export async function markStepFired(
  tx: Tx,
  taskId: TaskId,
  planVersion: number,
  seq: number,
  firedAt: Date,
): Promise<void> {
  await tx
    .update(pokeSteps)
    .set({ firedAt })
    .where(
      and(
        eq(pokeSteps.taskId, taskId),
        eq(pokeSteps.planVersion, planVersion),
        eq(pokeSteps.seq, seq),
      ),
    );
}

/** Something about the task changed that sync must re-send (new events, list visibility). */
export async function touchTask(tx: Tx, taskId: TaskId): Promise<void> {
  await tx.update(tasks).set({ changeSeq: nextSeq }).where(eq(tasks.id, taskId));
}

/**
 * Claim due timers (ADR 0007): a lease is set on rows selected with FOR UPDATE SKIP LOCKED, so
 * concurrent ticks never claim the same row and a crashed run is retried after the lease.
 */
export async function claimDueActions(
  db: Db,
  now: Date,
  leaseMs: number,
  limit: number,
): Promise<ClaimedAction[]> {
  const due = db
    .select({ id: scheduledActions.id })
    .from(scheduledActions)
    .where(
      and(
        isNull(scheduledActions.doneAt),
        lte(scheduledActions.dueAt, now),
        or(isNull(scheduledActions.claimedUntil), lte(scheduledActions.claimedUntil, now)),
      ),
    )
    .orderBy(asc(scheduledActions.dueAt))
    .limit(limit)
    .for("update", { skipLocked: true });
  const rows = await db
    .update(scheduledActions)
    .set({ claimedUntil: new Date(now.getTime() + leaseMs) })
    .where(inArray(scheduledActions.id, due))
    .returning();
  return rows
    .sort((a, b) => a.dueAt.getTime() - b.dueAt.getTime())
    .map((r) => ({ id: r.id, kind: r.kind, taskId: r.taskId, dueAt: r.dueAt, payload: r.payload }));
}

/** Lock a claimed timer; false if another run or a task transition already finished it. */
export async function lockOpenAction(tx: Tx, id: string): Promise<boolean> {
  const [row] = await tx
    .select({ doneAt: scheduledActions.doneAt })
    .from(scheduledActions)
    .where(eq(scheduledActions.id, id))
    .for("update");
  return row !== undefined && row.doneAt === null;
}

/** Run a claimed timer again at [dueAt] (a photo cleanup that Storage did not answer). */
export async function rescheduleAction(ex: Executor, id: string, dueAt: Date): Promise<void> {
  await ex
    .update(scheduledActions)
    .set({ dueAt, claimedUntil: null })
    .where(and(eq(scheduledActions.id, id), isNull(scheduledActions.doneAt)));
}

export async function markActionDone(tx: Tx, id: string, now: Date): Promise<void> {
  await tx.update(scheduledActions).set({ doneAt: now }).where(eq(scheduledActions.id, id));
}

// ---- idempotency ----------------------------------------------------------------------------

export async function findIdempotentResult(
  ex: Executor,
  callerId: PersonId,
  key: string,
): Promise<{ command: string; result: unknown } | null> {
  const [row] = await ex
    .select({ command: idempotencyKeys.command, result: idempotencyKeys.result })
    .from(idempotencyKeys)
    .where(and(eq(idempotencyKeys.callerId, callerId), eq(idempotencyKeys.key, key)));
  return row ?? null;
}

export async function saveIdempotentResult(
  tx: Tx,
  r: { callerId: PersonId; key: string; command: string; result: unknown; now: Date },
): Promise<void> {
  await tx.insert(idempotencyKeys).values({
    callerId: r.callerId,
    key: r.key,
    command: r.command,
    result: r.result,
    createdAt: r.now,
  });
}

// ---- sync -----------------------------------------------------------------------------------

export interface SyncRows {
  cursor: number;
  connections: { edge: Edge; connectedAt: Date; person: Person; devices: Device[] }[];
  sentInvites: (Invite & { createdAt: Date })[];
  receivedInvites: (Invite & { inviterName: string; createdAt: Date })[];
  tasks: (Task & {
    snoozesUsed: number;
    createdAt: Date;
    photoSentAt: Date | null;
    reRequestNote: string | null;
  })[];
  events: (TaskEvent & { id: string })[];
  plans: PokePlan[];
}

/** Everything that changed for `me` since `cursor` (ADR 0006). Run inside `inSyncReadTx`. */
export async function syncRows(tx: Tx, me: Person, cursor: number): Promise<SyncRows> {
  // No writer is in flight (shared lock), so every value handed out so far is committed or gone.
  const [seq] = await tx.execute<{ value: string }>(
    sql`select (case when is_called then last_value else 0 end)::text as value from sync_seq`,
  );
  const mine = or(eq(tasks.senderId, me.id), eq(tasks.recipientId, me.id));

  const edgeRows = await tx
    .select()
    .from(edges)
    .innerJoin(people, eq(people.id, edges.toPersonId))
    .where(and(eq(edges.fromPersonId, me.id), gt(edges.changeSeq, cursor)))
    .orderBy(asc(edges.changeSeq));
  const otherIds = edgeRows.map((r) => r.people.id);
  const otherDevices =
    otherIds.length === 0
      ? []
      : (await tx.select().from(devices).where(inArray(devices.personId, otherIds))).map(toDevice);

  const sent = await tx
    .select()
    .from(invites)
    .where(and(eq(invites.inviterId, me.id), gt(invites.changeSeq, cursor)))
    .orderBy(asc(invites.changeSeq));
  const received = await tx
    .select()
    .from(invites)
    .innerJoin(people, eq(people.id, invites.inviterId))
    .where(and(eq(invites.inviteePhone, me.phone), gt(invites.changeSeq, cursor)))
    .orderBy(asc(invites.changeSeq));

  const taskRows = await tx
    .select()
    .from(tasks)
    .where(and(mine, gt(tasks.changeSeq, cursor)))
    .orderBy(asc(tasks.changeSeq));
  const eventRows = await tx
    .select({ e: taskEvents })
    .from(taskEvents)
    .innerJoin(tasks, eq(tasks.id, taskEvents.taskId))
    .where(and(mine, gt(taskEvents.changeSeq, cursor)))
    .orderBy(asc(taskEvents.id));

  // The device needs it for the next snooze's length and "son hakkın" (task-machine nextSnooze).
  const taskIds = taskRows.map((t) => t.id);
  const snoozeRows =
    taskIds.length === 0
      ? []
      : await tx
          .select({ taskId: taskEvents.taskId, used: sql<number>`count(*)::int` })
          .from(taskEvents)
          .where(and(inArray(taskEvents.taskId, taskIds), eq(taskEvents.type, "snoozed")))
          .groupBy(taskEvents.taskId);
  const snoozesUsed = new Map(snoozeRows.map((r) => [r.taskId, r.used]));

  // The last photo and the last re-request note, for 4e, 4f and 4g. Events are few per task.
  const photoRows =
    taskIds.length === 0
      ? []
      : await tx
          .select()
          .from(taskEvents)
          .where(
            and(
              inArray(taskEvents.taskId, taskIds),
              inArray(taskEvents.type, ["photo_sent", "re_requested"]),
            ),
          )
          .orderBy(asc(taskEvents.id));
  const photoSentAt = new Map<string, Date>();
  const reRequestNote = new Map<string, string | null>();
  for (const e of photoRows) {
    if (e.type === "photo_sent") photoSentAt.set(e.taskId, e.createdAt);
    else {
      const note = e.payload.note;
      reRequestNote.set(e.taskId, typeof note === "string" ? note : null);
    }
  }

  const received_ = taskRows.map(toTask).filter((t) => t.recipientId === me.id);
  const plans: PokePlan[] = [];
  for (const t of received_) plans.push(await currentPlan(tx, t));

  return {
    cursor: Math.max(cursor, Number(seq?.value ?? 0)),
    connections: edgeRows.map((r) => ({
      edge: toEdge(r.edges),
      connectedAt: r.edges.createdAt,
      person: toPerson(r.people),
      devices: otherDevices.filter((d) => d.personId === r.people.id),
    })),
    sentInvites: sent.map((r) => ({ ...toInvite(r), createdAt: r.createdAt })),
    receivedInvites: received.map((r) => ({
      ...toInvite(r.invites),
      inviterName: r.people.displayName,
      createdAt: r.invites.createdAt,
    })),
    tasks: taskRows.map((t) => ({
      ...toTask(t),
      snoozesUsed: snoozesUsed.get(t.id) ?? 0,
      createdAt: t.createdAt,
      photoSentAt: photoSentAt.get(t.id) ?? null,
      reRequestNote: t.status === "REJECTED" ? (reRequestNote.get(t.id) ?? null) : null,
    })),
    events: eventRows.map((r) => ({ ...toEvent(r.e), id: String(r.e.id) })),
    plans,
  };
}

export async function findOtpAttempts(ex: Executor, phone: string): Promise<OtpAttempts> {
  const [row] = await ex.select().from(otpAttempts).where(eq(otpAttempts.phone, phone));
  return row ? { failures: row.failures, lockedUntil: row.lockedUntil } : noAttempts;
}

export async function saveOtpAttempts(
  ex: Executor,
  phone: string,
  state: OtpAttempts,
  now: Date,
): Promise<void> {
  const values = { failures: state.failures, lockedUntil: state.lockedUntil, updatedAt: now };
  await ex
    .insert(otpAttempts)
    .values({ phone, ...values })
    .onConflictDoUpdate({ target: otpAttempts.phone, set: values });
}

export async function clearOtpAttempts(ex: Executor, phone: string): Promise<void> {
  await ex.delete(otpAttempts).where(eq(otpAttempts.phone, phone));
}
