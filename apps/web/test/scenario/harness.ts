// Scenario harness (docs/testing.md, Scenario tests). The real API on a fresh Postgres database,
// simulated phones for each person, an injected clock and recording fakes. People act only through
// the generated TS client (ADR 0008); time moves only through `advanceTo` / `advanceBy`, which
// ring due device alarms and call the tick endpoint.
//
// Reads (`world.task`, `eventsOf`, `pendingActions`, ...) come from a snapshot of the database taken
// after every action, so assertions stay synchronous. People are named in snapshots and pushes
// ("selin", "eren") instead of their UUIDs, so scenarios read like the docs.
import { randomUUID } from "node:crypto";
import { asc } from "drizzle-orm";
import { v7 as uuidv7 } from "uuid";
import { afterEach } from "vitest";
import type { Extraction, MissingField } from "../../src/domain/extraction";
import { isQuiet } from "../../src/domain/quiet-hours";
import {
  deriveReachability,
  deviceReachability,
  type Device,
  type PermissionSummary,
  type Reachability,
} from "../../src/domain/reachability";
import type { Command, DomainError } from "../../src/domain/task-machine";
import type { ClaimedAction } from "../../src/domain/tick";
import type {
  Edge,
  EdgeType,
  Person,
  PersonId,
  PokeLevel,
  PokePlan,
  PushMessage,
  Task,
  TaskEvent,
  TaskId,
} from "../../src/domain/types";
import * as schema from "../../src/infra/db/schema";
import { toPerson, toTask } from "../../src/infra/db/store";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
import { aPerson, allPermissions } from "../builders";
import { FakeClock, FakePhotoStorage, RecordingPushSender } from "../fakes";
import { startTestServer, TICK_SECRET, type ApiClient, type TestServer } from "../support/server";

/** Sunday 2026-10-11 20:00 in Istanbul. */
const SCENARIO_START = "2026-10-11T20:00:00+03:00";

const live = new Set<World>();
afterEach(async () => {
  for (const world of live) await world.dispose();
  live.clear();
});

export interface Ring {
  taskId: TaskId;
  seq: number;
  planned: PokeLevel;
  rang: PokeLevel;
  at: Date;
}

type ApiResult<T> = { data?: T; error?: { error: { name: string } } };

/** Unwrap a 2xx response; anything else fails the scenario loudly. */
function ok<T>(r: ApiResult<T>, what: string): T {
  if (r.data === undefined) throw new Error(`${what} failed: ${JSON.stringify(r.error)}`);
  return r.data;
}

/** A recipient's phone: stores plan steps like Room and rings them like AlarmManager. */
export class SimPhone {
  readonly rings: Ring[] = [];
  readonly received: PushMessage[] = [];
  online = true;
  /** Pushes are lost while true (FCM delay); the phone still rings and reports. */
  pushesDelayed = false;
  /** Steps per task, from the latest plan version the phone has synced. */
  private readonly alarms = new Map<TaskId, PokePlan>();
  /** `taskId|version|seq` of alarms already handled (rung or skipped). */
  private readonly handled = new Set<string>();
  private cursor = "0";

  constructor(
    private readonly world: World,
    private readonly api: ApiClient,
    public device: Device,
  ) {}

  get personId(): PersonId {
    return this.device.personId;
  }

  /** Alarms set on the phone that have not gone off yet. */
  pendingAlarms(): { taskId: TaskId; version: number; seq: number; at: Date; level: PokeLevel }[] {
    return [...this.alarms.values()].flatMap((plan) =>
      plan.steps
        .filter((s) => s.level !== "HUMAN")
        .filter((s) => !this.handled.has(alarmKey(plan.taskId, plan.version, s.seq)))
        .map((s) => ({
          taskId: plan.taskId,
          version: plan.version,
          seq: s.seq,
          at: s.plannedAt,
          level: s.level,
        })),
    );
  }

  async receivePush(message: PushMessage): Promise<void> {
    if (!this.online || this.pushesDelayed || this.device.fcmToken === null) return;
    this.received.push(message);
    await this.sync();
  }

  /** GET /api/v1/sync from the last cursor and store newer plans. */
  async sync(): Promise<void> {
    if (!this.online || this.device.fcmToken === null) return;
    const data = ok(
      await this.api.GET("/api/v1/sync", {
        params: { query: { cursor: this.cursor, deviceId: this.device.id } },
      }),
      "sync",
    );
    this.cursor = data.cursor;
    this.device = { ...this.device, lastSeenAt: this.world.clock.now() };
    for (const plan of data.plans) {
      const local = this.alarms.get(plan.taskId);
      // Devices ignore steps from older plan versions.
      if (!local || plan.version > local.version) {
        this.alarms.set(plan.taskId, {
          taskId: plan.taskId,
          version: plan.version,
          steps: plan.steps.map((s) => ({
            seq: s.seq,
            plannedAt: new Date(s.plannedAt),
            level: s.level,
            firedAt: null,
          })),
        });
      }
    }
  }

  /** Ring every alarm due at `now`, re-checking quiet hours and permissions first. */
  async ringDue(now: Date): Promise<void> {
    if (this.device.fcmToken === null) return;
    if (this.online) this.device = { ...this.device, lastSeenAt: now };
    const person = this.world.person(this.personId);
    for (const alarm of this.pendingAlarms()) {
      if (alarm.at.getTime() > now.getTime()) continue;
      this.handled.add(alarmKey(alarm.taskId, alarm.version, alarm.seq));
      if (isQuiet(now, person.quietHours)) {
        this.world.violations.push(`planned in quiet hours: ${alarm.taskId}#${String(alarm.seq)}`);
        continue;
      }
      // FULL_SCREEN silently degrades to SOUND without the permission.
      const degrade =
        alarm.level === "FULL_SCREEN" && deviceReachability(this.device) !== "FULL_SCREEN";
      const rang = degrade ? "SOUND" : alarm.level;
      this.rings.push({
        taskId: alarm.taskId,
        seq: alarm.seq,
        planned: alarm.level,
        rang,
        at: now,
      });
      if (this.online && rang !== "HUMAN") {
        ok(
          await this.api.POST("/api/v1/commands/report_fired", {
            body: {
              idempotencyKey: uuidv7(),
              taskId: alarm.taskId,
              deviceId: this.device.id,
              planVersion: alarm.version,
              seq: alarm.seq,
              firedAt: now.toISOString(),
              level: rang,
              permissionMissing: degrade,
            },
          }),
          "report_fired",
        );
      }
    }
  }

  /** Done, Snooze and Can't today stop local alarms first, even offline (ADR 0005). */
  stopLocal(taskId: TaskId): void {
    this.alarms.delete(taskId);
  }

  /** The app logged out or the account is gone: this phone can no longer be reached. */
  forgetToken(): void {
    this.device = { ...this.device, fcmToken: null };
  }

  ringsFor(taskId: TaskId): Ring[] {
    return this.rings.filter((r) => r.taskId === taskId);
  }
}

function alarmKey(taskId: TaskId, version: number, seq: number): string {
  return `${taskId}|${String(version)}|${String(seq)}`;
}

type WriteResult =
  | { kind: "created"; taskId: TaskId }
  | { kind: "ask"; question: MissingField }
  | { kind: "rejected"; error: DomainError };

type CommandResult = { ok: true } | { ok: false; error: DomainError };

export class Client {
  constructor(
    private readonly world: World,
    readonly person: Person,
    readonly api: ApiClient,
  ) {}

  get id(): PersonId {
    return this.person.id;
  }

  get phone(): SimPhone {
    return this.world.phoneOf(this.id);
  }

  /** Write free text in the one input box. */
  async write(text: string): Promise<WriteResult> {
    const r = await this.api.POST("/api/v1/commands/write_task", {
      body: { idempotencyKey: uuidv7(), text },
    });
    await this.world.refresh();
    if (r.error) return { kind: "rejected", error: r.error.error.name as DomainError };
    const data = ok(r, "write_task");
    if (data.kind === "created" && data.taskId !== null) {
      return { kind: "created", taskId: data.taskId };
    }
    if (data.kind === "ask" && data.question !== null)
      return { kind: "ask", question: data.question };
    throw new Error(`unexpected write_task result ${JSON.stringify(data)}`);
  }

  async command(taskId: TaskId, cmd: Command): Promise<CommandResult> {
    if (cmd.type === "done" || cmd.type === "cant_today" || cmd.type === "photo_sent") {
      this.phone.stopLocal(taskId);
    }
    const r = await this.send(taskId, cmd);
    await this.world.refresh();
    return r.error ? { ok: false, error: r.error.error.name as DomainError } : { ok: true };
  }

  private send(taskId: TaskId, cmd: Command): Promise<ApiResult<unknown>> {
    const k = { idempotencyKey: uuidv7(), taskId };
    const post = (path: string, body: object) =>
      this.api.POST(path as "/api/v1/commands/done", { body: { ...k, ...body } });
    switch (cmd.type) {
      case "done":
        return post("/api/v1/commands/done", { cameraUnavailable: cmd.cameraUnavailable });
      case "photo_sent":
        return post("/api/v1/commands/photo_sent", { photoPath: cmd.photoPath });
      case "cant_today":
        return post("/api/v1/commands/cant_today", { note: cmd.note });
      case "did_it_later":
        return post("/api/v1/commands/did_it_later", {});
      case "snooze":
        return post("/api/v1/commands/snooze", {});
      case "cancel":
        return post("/api/v1/commands/cancel", { note: cmd.note });
      case "edit":
        return post("/api/v1/commands/edit", {
          deadline: cmd.deadline?.toISOString(),
          detail: cmd.detail,
          finishMode: cmd.finishMode,
        });
      case "approve":
        return post("/api/v1/commands/approve", {});
      case "re_request":
        return post("/api/v1/commands/re_request", { note: cmd.note });
      case "new_deadline":
        return post("/api/v1/commands/new_deadline", { deadline: cmd.deadline.toISOString() });
      case "sender_handles":
        return post("/api/v1/commands/sender_handles", {});
      case "deadline_passed":
      case "approval_timeout":
      case "account_deleted":
        throw new Error(`${cmd.type} is a server command`);
    }
  }

  async sendPhoto(taskId: TaskId): Promise<CommandResult> {
    const upload = ok(
      await this.api.POST("/api/v1/photos/upload-url", { body: { taskId } }),
      "upload-url",
    );
    return this.command(taskId, { type: "photo_sent", photoPath: upload.path });
  }

  async invite(phone: string, relationship: EdgeType, prefilledName: string): Promise<string> {
    const r = ok(
      await this.api.POST("/api/v1/commands/create_invite", {
        body: { idempotencyKey: uuidv7(), phone, relationship, prefilledName },
      }),
      "create_invite",
    );
    await this.world.refresh();
    return r.code;
  }

  async acceptInvite(code: string, relationship: EdgeType): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/accept_invite", {
        body: { idempotencyKey: uuidv7(), code, relationship },
      }),
      "accept_invite",
    );
    await this.world.refresh();
  }

  async declineInvite(code: string): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/decline_invite", {
        body: { idempotencyKey: uuidv7(), code },
      }),
      "decline_invite",
    );
    await this.world.refresh();
  }

  async logOut(): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/log_out", {
        body: { idempotencyKey: uuidv7(), deviceId: this.phone.device.id },
      }),
      "log_out",
    );
    this.phone.forgetToken();
    await this.world.refresh();
  }

  async deleteAccount(): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/delete_account", {
        body: { idempotencyKey: uuidv7() },
      }),
      "delete_account",
    );
    this.phone.forgetToken();
    await this.world.refresh();
  }
}

interface Snapshot {
  people: Map<PersonId, Person>;
  tasks: Map<TaskId, Task>;
  events: TaskEvent[];
  plans: Map<TaskId, PokePlan[]>;
  pendingActions: ClaimedAction[];
  edges: Edge[];
  devices: Device[];
  invitesSeen: Map<PersonId, { code: string; status: string }[]>;
}

const emptySnapshot = (): Snapshot => ({
  people: new Map(),
  tasks: new Map(),
  events: [],
  plans: new Map(),
  pendingActions: [],
  edges: [],
  devices: [],
  invitesSeen: new Map(),
});

export class World {
  readonly clock = new FakeClock(SCENARIO_START);
  /** Pushes as the fake FCM saw them, addressed by name. */
  readonly push = new RecordingPushSender(this.clock);
  readonly photos = new FakePhotoStorage();
  readonly extractor: FixtureExtractor;
  /** Device-side rule breaks (ringing in quiet hours). Scenarios assert this stays empty. */
  readonly violations: string[] = [];

  private readonly ready: Promise<TestServer>;
  private readonly clients = new Map<PersonId, Client>();
  private readonly phones = new Map<PersonId, SimPhone>();
  /** UUID to scenario name ("eren"). */
  private readonly names = new Map<string, PersonId>();
  private snap = emptySnapshot();

  constructor(fixtures: Record<string, Extraction> = {}) {
    this.extractor = new FixtureExtractor(new Map(Object.entries(fixtures)));
    this.ready = startTestServer({
      clock: this.clock,
      extractor: this.extractor,
      photos: this.photos,
      push: {
        send: async (message: PushMessage) => {
          const named = { ...message, to: this.nameOf(message.to) };
          await this.push.send(named);
          await this.phones.get(named.to)?.receivePush(named);
        },
      },
    });
    live.add(this);
  }

  async dispose(): Promise<void> {
    await (await this.ready).close();
  }

  private nameOf(id: string): PersonId {
    return this.names.get(id) ?? id;
  }

  // ---- setup ---------------------------------------------------------------------------------

  /** A signed-up person with one phone and every permission granted. */
  async addPerson(name: string, permissions: PermissionSummary = allPermissions): Promise<Client> {
    const server = await this.ready;
    const person = aPerson(name);
    const uuid = randomUUID();
    this.names.set(uuid, person.id);
    const api = server.client(await server.tokenFor(uuid, person.phone));

    ok(
      await api.POST("/api/v1/commands/register", {
        body: {
          idempotencyKey: uuidv7(),
          displayName: person.displayName,
          timeZone: person.quietHours.timeZone,
        },
      }),
      "register",
    );
    const fcmToken = `token-${person.id}`;
    const registered = ok(
      await api.POST("/api/v1/devices", {
        body: { deviceId: null, fcmToken, platform: "android", appVersion: "test", permissions },
      }),
      "devices",
    );
    const device: Device = {
      id: registered.deviceId,
      personId: person.id,
      fcmToken,
      permissions,
      lastSeenAt: this.clock.now(),
    };
    const client = new Client(this, person, api);
    this.clients.set(person.id, client);
    this.phones.set(person.id, new SimPhone(this, api, device));
    await this.refresh();
    return client;
  }

  /** Invite and accept in one step, for scenarios that start with a connection. */
  async connect(
    inviter: Client,
    invitee: Client,
    inviterSays: EdgeType,
    inviteeSays: EdgeType,
  ): Promise<void> {
    const code = await inviter.invite(
      invitee.person.phone,
      inviterSays,
      invitee.person.displayName,
    );
    await invitee.acceptInvite(code, inviteeSays);
  }

  // ---- reads (from the latest snapshot) -------------------------------------------------------

  person(id: PersonId): Person {
    const p = this.snap.people.get(id);
    if (!p) throw new Error(`no person ${id}`);
    return p;
  }

  task(id: TaskId): Task {
    const t = this.snap.tasks.get(id);
    if (!t) throw new Error(`no task ${id}`);
    return t;
  }

  phoneOf(id: PersonId): SimPhone {
    const p = this.phones.get(id);
    if (!p) throw new Error(`no phone for ${id}`);
    return p;
  }

  currentPlan(taskId: TaskId): PokePlan | undefined {
    return this.snap.plans.get(taskId)?.at(-1);
  }

  plansOf(taskId: TaskId): PokePlan[] {
    return this.snap.plans.get(taskId) ?? [];
  }

  eventsOf(taskId: TaskId): TaskEvent[] {
    return this.snap.events.filter((e) => e.taskId === taskId);
  }

  edgesOf(id: PersonId): Edge[] {
    return this.snap.edges.filter((e) => e.fromPersonId === id || e.toPersonId === id);
  }

  reachabilityOf(id: PersonId): Reachability {
    return deriveReachability(this.snap.devices.filter((d) => d.personId === id));
  }

  /** Invites as the inviter sees them through GET /api/v1/sync. */
  invitesSeenBy(inviterId: PersonId): { code: string; status: string }[] {
    return this.snap.invitesSeen.get(inviterId) ?? [];
  }

  pendingActions(taskId?: TaskId): ClaimedAction[] {
    return this.snap.pendingActions.filter((a) => taskId === undefined || a.taskId === taskId);
  }

  /** Re-read the database (and each person's invites through sync) after an action. */
  async refresh(): Promise<void> {
    const { db } = (await this.ready).database;
    const named = (id: string) => this.nameOf(id);
    const snap = emptySnapshot();

    for (const row of await db.select().from(schema.people)) {
      const p = toPerson(row);
      snap.people.set(named(p.id), { ...p, id: named(p.id) });
    }
    for (const row of await db.select().from(schema.tasks)) {
      const t = toTask(row);
      snap.tasks.set(t.id, {
        ...t,
        senderId: named(t.senderId),
        recipientId: named(t.recipientId),
      });
    }
    snap.events = (
      await db.select().from(schema.taskEvents).orderBy(asc(schema.taskEvents.id))
    ).map((e) => ({
      taskId: e.taskId,
      actorId: e.actorId === null ? null : named(e.actorId),
      type: e.type,
      payload: e.payload,
      at: e.createdAt,
    }));
    const steps = await db
      .select()
      .from(schema.pokeSteps)
      .orderBy(asc(schema.pokeSteps.planVersion), asc(schema.pokeSteps.seq));
    for (const t of snap.tasks.values()) {
      const versions: PokePlan[] = [];
      for (let v = 1; v <= t.planVersion; v++) {
        versions.push({
          taskId: t.id,
          version: v,
          steps: steps
            .filter((s) => s.taskId === t.id && s.planVersion === v)
            .map((s) => ({
              seq: s.seq,
              plannedAt: s.plannedAt,
              level: s.level,
              firedAt: s.firedAt,
            })),
        });
      }
      snap.plans.set(t.id, versions);
    }
    snap.pendingActions = (await db.select().from(schema.scheduledActions))
      .filter((a) => a.doneAt === null)
      .map((a) => ({
        id: a.id,
        kind: a.kind,
        taskId: a.taskId,
        dueAt: a.dueAt,
        payload: a.payload,
      }));
    snap.edges = (await db.select().from(schema.edges).orderBy(asc(schema.edges.changeSeq))).map(
      (e) => ({
        fromPersonId: named(e.fromPersonId),
        toPersonId: named(e.toPersonId),
        type: e.type,
        status: e.status,
      }),
    );
    snap.devices = (await db.select().from(schema.devices)).map((d) => ({
      id: d.id,
      personId: named(d.personId),
      fcmToken: d.fcmToken,
      permissions: d.permissions,
      lastSeenAt: d.lastSeenAt,
    }));
    for (const [name, client] of this.clients) {
      const r = await client.api.GET("/api/v1/sync", { params: { query: {} } });
      // A deleted account can no longer sync.
      if (r.data) {
        snap.invitesSeen.set(
          name,
          r.data.sentInvites.map((i) => ({ code: i.code, status: i.status })),
        );
      }
    }
    this.snap = snap;
  }

  // ---- time ----------------------------------------------------------------------------------

  /** Move time forward, stopping at every alarm and timer on the way. */
  async advanceTo(target: Date | string): Promise<void> {
    const end = new Date(target).getTime();
    for (;;) {
      const next = this.nextEventTime();
      if (next === null || next > end) break;
      this.clock.set(new Date(Math.max(next, this.clock.now().getTime())));
      await this.runDue();
    }
    this.clock.set(new Date(end));
    await this.runDue();
  }

  async advanceBy(ms: number): Promise<void> {
    await this.advanceTo(new Date(this.clock.now().getTime() + ms));
  }

  /** Phones ring what is due, then the server timers run, as pg_cron would trigger them. */
  private async runDue(): Promise<void> {
    const now = this.clock.now();
    for (const phone of this.phones.values()) await phone.ringDue(now);
    const response = await (
      await this.ready
    ).app.request("/api/internal/tick", {
      method: "POST",
      headers: { Authorization: `Bearer ${TICK_SECRET}` },
    });
    if (response.status !== 200) throw new Error(`tick failed: ${String(response.status)}`);
    await this.refresh();
  }

  private nextEventTime(): number | null {
    const now = this.clock.now().getTime();
    const times = [
      ...this.pendingActions().map((a) => a.dueAt.getTime()),
      ...[...this.phones.values()].flatMap((p) =>
        p.device.fcmToken === null ? [] : p.pendingAlarms().map((a) => a.at.getTime()),
      ),
    ].filter((t) => t > now);
    return times.length === 0 ? null : Math.min(...times);
  }
}
