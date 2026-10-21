// Scenario harness (docs/testing.md, Scenario tests). The real API on a fresh Postgres database,
// simulated phones for each person, an injected clock and recording fakes. People act only through
// the generated TS client (ADR 0008); time moves only through `advanceTo` / `advanceBy`, which
// ring due device alarms and call the tick endpoint.
//
// Reads (`world.task`, `eventsOf`, `pendingActions`, ...) come from a snapshot of the database taken
// after every action, so assertions stay synchronous. People are named in snapshots and pushes
// ("sibel", "eren") instead of their UUIDs, so scenarios read like the docs.
import { randomUUID } from "node:crypto";
import { asc, eq, inArray, or } from "drizzle-orm";
import { v7 as uuidv7 } from "uuid";
import { afterEach } from "vitest";
import type { MissingField } from "../../src/domain/extraction";
import type { PersonProfiler, PokePlanner } from "../../src/domain/ports";
import { announce, type AnnounceResult } from "../../src/api/services";
import type { AnnouncementContent } from "../../src/domain/announcements";
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
import { FixtureExtractor, type FixtureExtraction } from "../../src/infra/llm/fixture-extractor";
import { aPerson, allPermissions } from "../builders";
import { FakeAuthGateway, FakeClock, FakePhotoStorage, RecordingPushSender } from "../fakes";
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
  /**
   * The phone brand kills the app's alarms while true (docs/ux/platform.md, Missed-ring detection): due alarms are
   * gone without ringing, while the phone stays online.
   */
  alarmsKilled = false;
  /** Steps per task, from the latest plan version the phone has synced. */
  private readonly alarms = new Map<TaskId, PokePlan>();
  /** `taskId|version|seq` of alarms already handled (rung or skipped). */
  private readonly handled = new Set<string>();
  private cursor = "0";
  /** What the last sync answered: what the app would store (connections, me). */
  lastSync: {
    me: { avatarPath?: string | null };
    connections: { personId: string; avatarPath?: string | null }[];
  } | null = null;
  /** Snoozes used per task, as the last sync said. */
  private readonly snoozes = new Map<TaskId, number>();
  /** Task events the phone has synced, oldest first: what the app builds cards, the bell and 7a from. */
  readonly events: { taskId: TaskId; type: string; payload: Record<string, unknown> }[] = [];
  /** Announcements as the phone stores them, by id: the last text synced (ADR 0025). */
  readonly announcements = new Map<string, { title: string; summary: string; body: string }>();
  /** Every announcement row a sync brought, in order: a repeated id is a text update. */
  readonly announcementSyncs: string[] = [];

  constructor(
    private readonly world: World,
    private readonly api: ApiClient,
    public device: Device,
  ) {}

  get personId(): PersonId {
    return this.device.personId;
  }

  /** What the phone knows about the snoozes of a task (NUD-36). */
  snoozesUsed(taskId: TaskId): number | undefined {
    return this.snoozes.get(taskId);
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
    if (!this.online || this.pushesDelayed || this.device.fid === null) return;
    this.received.push(message);
    await this.sync();
  }

  /** Whether each task is on this phone's list, as the last sync said (a missed card leaves it, NUD-49). */
  readonly inList = new Map<TaskId, boolean>();

  /** What a sync without a cursor answers, as after sign-in; the phone's own state is untouched. */
  async fullSync() {
    return ok(await this.api.GET("/api/v1/sync", { params: { query: {} } }), "full sync");
  }

  /** GET /api/v1/sync from the last cursor and store newer plans. */
  async sync(): Promise<void> {
    if (!this.online || this.device.fid === null) return;
    const data = ok(
      await this.api.GET("/api/v1/sync", {
        params: { query: { cursor: this.cursor, deviceId: this.device.id } },
      }),
      "sync",
    );
    this.cursor = data.cursor;
    this.lastSync = data;
    this.device = { ...this.device, lastSeenAt: this.world.clock.now() };
    for (const task of data.tasks) {
      this.snoozes.set(task.id, task.snoozesUsed ?? 0);
      this.inList.set(task.id, task.inRecipientList);
    }
    for (const e of data.events)
      this.events.push({ taskId: e.taskId, type: e.type, payload: e.payload });
    for (const a of data.announcements ?? []) {
      this.announcementSyncs.push(a.id);
      this.announcements.set(a.id, { title: a.title, summary: a.summary, body: a.body });
    }
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
    if (this.device.fid === null) return;
    if (this.online) this.device = { ...this.device, lastSeenAt: now };
    const person = this.world.person(this.personId);
    for (const alarm of this.pendingAlarms()) {
      if (alarm.at.getTime() > now.getTime()) continue;
      this.handled.add(alarmKey(alarm.taskId, alarm.version, alarm.seq));
      if (this.alarmsKilled) continue;
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
  forgetFid(): void {
    this.device = { ...this.device, fid: null };
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
  | {
      kind: "ask";
      question: MissingField | "quiet_hours";
      quiet?: { start: string; end: string; suggested: string };
    }
  | { kind: "chat"; reply: string }
  | { kind: "rejected"; error: DomainError };

type CommandResult = { ok: true } | { ok: false; error: DomainError };

export class Client {
  constructor(
    private readonly world: World,
    readonly person: Person,
    readonly api: ApiClient,
    /** The real id on the server; `person.id` is the scenario name. */
    readonly uuid: string,
  ) {}

  get id(): PersonId {
    return this.person.id;
  }

  get phone(): SimPhone {
    return this.world.phoneOf(this.id);
  }

  /** "Kaldır" on a missed card (NUD-49). */
  async removeMissed(taskId: TaskId): Promise<CommandResult> {
    const r = await this.api.POST("/api/v1/commands/remove_missed", {
      body: { idempotencyKey: uuidv7(), taskId },
    });
    await this.world.refresh();
    return r.error ? { ok: false, error: r.error.error.name as DomainError } : { ok: true };
  }

  /** The phone says a task reached it, or (seen) that its person has seen it (NUD-68). */
  async acknowledge(taskId: TaskId, seen: boolean): Promise<CommandResult> {
    const r = await this.api.POST("/api/v1/commands/acknowledge", {
      body: { idempotencyKey: uuidv7(), taskId, seen },
    });
    await this.world.refresh();
    return r.error ? { ok: false, error: r.error.error.name as DomainError } : { ok: true };
  }

  /** Write free text in the one input box. A text queued offline (6a) is sent again with its [idempotencyKey]. */
  async write(text: string, idempotencyKey: string = uuidv7()): Promise<WriteResult> {
    const r = await this.api.POST("/api/v1/commands/write_task", {
      body: { idempotencyKey, text },
    });
    await this.world.refresh();
    if (r.error) return { kind: "rejected", error: r.error.error.name as DomainError };
    const data = ok(r, "write_task");
    if (data.kind === "created" && data.taskId !== null) {
      return { kind: "created", taskId: data.taskId };
    }
    if (data.kind === "ask" && data.question !== null) {
      return data.quiet
        ? { kind: "ask", question: data.question, quiet: data.quiet }
        : { kind: "ask", question: data.question };
    }
    if (data.kind === "chat" && data.reply) return { kind: "chat", reply: data.reply };
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
        return post("/api/v1/commands/done", {
          cameraUnavailable: cmd.cameraUnavailable,
          doneAt: cmd.doneAt?.toISOString(),
        });
      case "photo_sent":
        return post("/api/v1/commands/photo_sent", {
          photoPath: cmd.photoPath,
          takenAt: cmd.takenAt?.toISOString(),
        });
      case "cant_today":
        return post("/api/v1/commands/cant_today", { note: cmd.note });
      case "did_it_later":
        return post("/api/v1/commands/did_it_later", {});
      case "snooze":
        return post("/api/v1/commands/snooze", {});
      case "set_start":
        return post("/api/v1/commands/set_start", { startAt: cmd.startAt.toISOString() });
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
      case "reassign":
        return post("/api/v1/commands/reassign", { recipientId: cmd.recipientId });
      case "deadline_passed":
      case "approval_timeout":
      case "account_deleted":
      case "connection_removed":
      case "replan":
        throw new Error(`${cmd.type} is a server command`);
    }
  }

  /** Kime changed in 7c (NUD-114): the new task's id, or the error. */
  async reassign(
    taskId: TaskId,
    to: Client,
  ): Promise<{ ok: true; taskId: TaskId } | { ok: false; error: DomainError }> {
    const r = await this.send(taskId, { type: "reassign", recipientId: to.uuid });
    await this.world.refresh();
    if (r.error) return { ok: false, error: r.error.error.name as DomainError };
    return { ok: true, taskId: (r.data as { taskId: TaskId }).taskId };
  }

  /** Gönder; [takenAt] is when the photo was taken, for an upload that comes late (6e). */
  async sendPhoto(taskId: TaskId, takenAt?: Date): Promise<CommandResult> {
    const iso = takenAt?.toISOString();
    const upload = await this.api.POST("/api/v1/photos/upload-url", {
      body: { taskId, takenAt: iso },
    });
    if (!upload.data) return { ok: false, error: upload.error.error.name as DomainError };
    return this.command(taskId, { type: "photo_sent", photoPath: upload.data.path, takenAt });
  }

  async invite(phone: string, relationship: EdgeType, prefilledName: string): Promise<string> {
    const r = ok(
      await this.api.POST("/api/v1/commands/create_invite", {
        body: { idempotencyKey: uuidv7(), phone, relationship, prefilledName },
      }),
      "create_invite",
    );
    this.world.inviteIds.set(r.code, r.inviteId);
    await this.world.refresh();
    return r.code;
  }

  async revokeInvite(code: string): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/revoke_invite", {
        body: { idempotencyKey: uuidv7(), inviteId: this.world.inviteIds.get(code) ?? code },
      }),
      "revoke_invite",
    );
    await this.world.refresh();
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

  /** Profile picture (NUD-72): the upload URL, then the path made mine. */
  async avatarUploadUrl(): Promise<{ path: string; url: string }> {
    return ok(await this.api.POST("/api/v1/avatar/upload-url"), "avatar/upload-url");
  }

  async setAvatar(avatarPath: string | null): Promise<ApiResult<{ avatarPath?: string | null }>> {
    const r = await this.api.POST("/api/v1/commands/set_avatar", {
      body: { idempotencyKey: uuidv7(), avatarPath },
    });
    await this.world.refresh();
    return r;
  }

  async avatarViewUrl(personId: PersonId): Promise<ApiResult<{ url: string; avatarPath: string }>> {
    return this.api.POST("/api/v1/avatar/view-url", { body: { personId } });
  }

  /** Quiet hours (7g) and the human channel switches (5a). */
  async updateSettings(patch: {
    quietHours?: { start: string; end: string };
    useConnectionsForMyTasks?: boolean;
    notifyMeForOthersTasks?: boolean;
  }): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/update_settings", {
        body: { idempotencyKey: uuidv7(), ...patch },
      }),
      "update_settings",
    );
    await this.world.refresh();
  }

  /** Person detail (5b): my relationship to them and my switch for their tasks. */
  async updateConnection(
    other: Client,
    patch: { relationship?: EdgeType; notifyMe?: boolean },
  ): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/update_connection", {
        body: { idempotencyKey: uuidv7(), personId: other.uuid, ...patch },
      }),
      "update_connection",
    );
    await this.world.refresh();
  }

  async removeConnection(other: Client): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/remove_connection", {
        body: { idempotencyKey: uuidv7(), personId: other.uuid },
      }),
      "remove_connection",
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
    this.phone.forgetFid();
    await this.world.refresh();
  }

  async deleteAccount(): Promise<void> {
    ok(
      await this.api.POST("/api/v1/commands/delete_account", {
        body: { idempotencyKey: uuidv7() },
      }),
      "delete_account",
    );
    this.phone.forgetFid();
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
  invitesReceived: Map<PersonId, { code: string; status: string }[]>;
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
  invitesReceived: new Map(),
});

export class World {
  readonly clock = new FakeClock(SCENARIO_START);
  /** Pushes as the fake FCM saw them, addressed by name. */
  readonly push = new RecordingPushSender(this.clock);
  readonly photos = new FakePhotoStorage();
  readonly extractor: FixtureExtractor;
  /** Device-side rule breaks (ringing in quiet hours). Scenarios assert this stays empty. */
  readonly violations: string[] = [];
  /** Invite ids by code, so anyone in a scenario can try to revoke an invite by its code. */
  readonly inviteIds = new Map<string, string>();

  private readonly ready: Promise<TestServer>;
  private readonly clients = new Map<PersonId, Client>();
  private readonly phones = new Map<PersonId, SimPhone>();
  /** UUID to scenario name ("eren"). */
  private readonly names = new Map<string, PersonId>();
  private snap = emptySnapshot();

  constructor(
    fixtures: Record<string, FixtureExtraction> = {},
    options: { planner?: PokePlanner; profiler?: PersonProfiler } = {},
  ) {
    this.extractor = new FixtureExtractor(new Map(Object.entries(fixtures)));
    this.ready = startTestServer({
      clock: this.clock,
      extractor: this.extractor,
      planner: options.planner ?? null,
      profiler: options.profiler ?? null,
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
    const fid = `fid-${person.id}`;
    const registered = ok(
      await api.POST("/api/v1/devices", {
        body: { deviceId: null, fid, platform: "android", appVersion: "test", permissions },
      }),
      "devices",
    );
    const device: Device = {
      id: registered.deviceId,
      personId: person.id,
      fid,
      permissions,
      lastSeenAt: this.clock.now(),
    };
    const client = new Client(this, person, api, uuid);
    this.clients.set(person.id, client);
    this.phones.set(person.id, new SimPhone(this, api, device));
    await this.refresh();
    return client;
  }

  /** `mise run announce`: `to` is people in the scenario or "*" (ADR 0025). */
  async announce(
    content: AnnouncementContent,
    to: readonly (Client | string)[] | "*",
    confirm = true,
  ): Promise<AnnounceResult> {
    const server = await this.ready;
    const phones = to === "*" ? "*" : to.map((c) => (typeof c === "string" ? c : c.person.phone));
    const result = await announce(server.deps, content, phones, confirm);
    await this.refresh();
    return result;
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
  /** Every task [senderId] wrote, in no particular order. */
  tasksWrittenBy(senderId: PersonId): Task[] {
    return [...this.snap.tasks.values()].filter((t) => t.senderId === senderId);
  }

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

  invitesReceivedBy(inviteeId: PersonId): { code: string; status: string }[] {
    return this.snap.invitesReceived.get(inviteeId) ?? [];
  }

  pendingActions(taskId?: TaskId): ClaimedAction[] {
    return this.snap.pendingActions.filter((a) => taskId === undefined || a.taskId === taskId);
  }

  /** Re-read the database (and each person's invites through sync) after an action. */
  /** The LLM call log (ADR 0019), oldest first, with people by name. */
  async llmCalls(): Promise<(typeof schema.llmCalls.$inferSelect)[]> {
    const { db } = (await this.ready).database;
    const rows = await db.select().from(schema.llmCalls).orderBy(asc(schema.llmCalls.createdAt));
    return rows.map((r) => ({ ...r, personId: this.nameOf(r.personId) }));
  }

  /**
   * Earlier LLM calls about the person, as a busy day would leave them, for the daily caps
   * (ADR 0024). `at` defaults to the scenario clock.
   */
  async fillLlmCalls(
    person: Client,
    kind: "extraction" | "plan" | "profile",
    count: number,
    at: Date = this.clock.now(),
  ): Promise<void> {
    const { db } = (await this.ready).database;
    await db.insert(schema.llmCalls).values(
      Array.from({ length: count }, () => ({
        id: randomUUID(),
        kind,
        personId: person.uuid,
        input: {},
        outcome: "filled",
        latencyMs: 0,
        createdAt: at,
      })),
    );
  }

  /** Sign-in codes the Send SMS hook sent to the person's number (ADR 0024). */
  async fillSmsSends(person: Client, count: number): Promise<void> {
    const { db } = (await this.ready).database;
    await db.insert(schema.smsSends).values(
      Array.from({ length: count }, () => ({
        id: randomUUID(),
        phone: person.person.phone,
        sentAt: this.clock.now(),
      })),
    );
  }

  /**
   * How many rows in each table still hold the person's id or phone, by table (NUD-89). Task rows
   * count once per task the person sent or received; events and steps count through them.
   */
  async rowsHolding(person: Client): Promise<Record<string, number>> {
    const { db } = (await this.ready).database;
    const id = person.uuid;
    const phone = person.person.phone;
    const theirTasks = db
      .select({ id: schema.tasks.id })
      .from(schema.tasks)
      .where(or(eq(schema.tasks.senderId, id), eq(schema.tasks.recipientId, id)));
    const count = async (rows: Promise<unknown[]>) => (await rows).length;
    const counts: Record<string, number> = {
      people: await count(
        db
          .select()
          .from(schema.people)
          .where(or(eq(schema.people.id, id), eq(schema.people.phone, phone))),
      ),
      tasks: await count(
        db
          .select()
          .from(schema.tasks)
          .where(or(eq(schema.tasks.senderId, id), eq(schema.tasks.recipientId, id))),
      ),
      taskEvents: await count(
        db.select().from(schema.taskEvents).where(inArray(schema.taskEvents.taskId, theirTasks)),
      ),
      pokeSteps: await count(
        db.select().from(schema.pokeSteps).where(inArray(schema.pokeSteps.taskId, theirTasks)),
      ),
      edges: await count(
        db
          .select()
          .from(schema.edges)
          .where(or(eq(schema.edges.fromPersonId, id), eq(schema.edges.toPersonId, id))),
      ),
      invites: await count(
        db
          .select()
          .from(schema.invites)
          .where(or(eq(schema.invites.inviterId, id), eq(schema.invites.inviteePhone, phone))),
      ),
      devices: await count(db.select().from(schema.devices).where(eq(schema.devices.personId, id))),
      llmCalls: await count(
        db.select().from(schema.llmCalls).where(eq(schema.llmCalls.personId, id)),
      ),
      idempotencyKeys: await count(
        db.select().from(schema.idempotencyKeys).where(eq(schema.idempotencyKeys.callerId, id)),
      ),
      otpAttempts: await count(
        db.select().from(schema.otpAttempts).where(eq(schema.otpAttempts.phone, phone)),
      ),
      smsSends: await count(
        db.select().from(schema.smsSends).where(eq(schema.smsSends.phone, phone)),
      ),
      announcementRecipients: await count(
        db
          .select()
          .from(schema.announcementRecipients)
          .where(eq(schema.announcementRecipients.personId, id)),
      ),
    };
    return Object.fromEntries(Object.entries(counts).filter(([, n]) => n > 0));
  }

  /** The Supabase auth users deleted so far, by the person's server id. */
  async deletedAuthUsers(): Promise<string[]> {
    const auth = (await this.ready).deps.auth as FakeAuthGateway;
    return [...auth.deletedUsers];
  }

  /** Makes the next auth user deletion fail, as a Supabase outage would. */
  async failNextAuthDelete(): Promise<void> {
    ((await this.ready).deps.auth as FakeAuthGateway).failNextDelete = true;
  }

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
        notifyMe: e.notifyMe,
      }),
    );
    snap.devices = (await db.select().from(schema.devices)).map((d) => ({
      id: d.id,
      personId: named(d.personId),
      fid: d.fid,
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
        snap.invitesReceived.set(
          name,
          r.data.receivedInvites.map((i) => ({ code: i.code, status: i.status })),
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
        p.device.fid === null ? [] : p.pendingAlarms().map((a) => a.at.getTime()),
      ),
    ].filter((t) => t > now);
    return times.length === 0 ? null : Math.min(...times);
  }
}
