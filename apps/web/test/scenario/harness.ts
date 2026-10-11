// Scenario harness (docs/testing.md, Scenario tests). An in-memory server built from the domain
// functions, simulated phones for each person, an injected clock and recording fakes. Time moves
// only through `advanceTo` / `advanceBy`, which ring due device alarms and run `tick(now)`.
//
// Stage 3 keeps state in memory. Stage 4 replaces the in-memory server with the HTTP API, Postgres
// repositories and the generated TS client; scenario files should not need to change much.
import {
  acceptInvite,
  createInvite,
  declineInvite,
  deleteAccount,
  inviteStatusForInviter,
  type Invite,
} from "../../src/domain/connections";
import { resolveExtraction, type Extraction, type MissingField } from "../../src/domain/extraction";
import { isQuiet } from "../../src/domain/quiet-hours";
import {
  deriveReachability,
  deviceReachability,
  logOut,
  type Device,
  type PermissionSummary,
  type Reachability,
} from "../../src/domain/reachability";
import {
  applyCommand,
  createTask,
  type Command,
  type DomainError,
  type Outcome,
  type TaskContext,
} from "../../src/domain/task-machine";
import {
  reportFired,
  tick,
  type ActionContext,
  type ActionResult,
  type ClaimedAction,
  type FiredReport,
  type TickPort,
} from "../../src/domain/tick";
import type {
  EdgeType,
  Edge,
  Person,
  PersonId,
  PokeLevel,
  PokePlan,
  PushMessage,
  Task,
  TaskEvent,
  TaskId,
} from "../../src/domain/types";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
import { aPerson, allPermissions } from "../builders";
import { FakeClock, FakePhotoStorage, RecordingPushSender } from "../fakes";

/** Sunday 2026-10-11 20:00 in Istanbul. */
const SCENARIO_START = "2026-10-11T20:00:00+03:00";

export interface Ring {
  taskId: TaskId;
  seq: number;
  planned: PokeLevel;
  rang: PokeLevel;
  at: Date;
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

  constructor(
    private readonly world: World,
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

  receivePush(message: PushMessage): void {
    if (!this.online || this.pushesDelayed || this.device.fcmToken === null) return;
    this.received.push(message);
    this.sync();
  }

  /** Pull the current plans of every task this person receives. */
  sync(): void {
    if (!this.online || this.device.fcmToken === null) return;
    this.device = { ...this.device, lastSeenAt: this.world.clock.now() };
    for (const task of this.world.tasksReceivedBy(this.personId)) {
      const plan = this.world.currentPlan(task.id);
      const local = this.alarms.get(task.id);
      // Devices ignore steps from older plan versions.
      if (plan && (!local || plan.version > local.version)) {
        this.alarms.set(task.id, { ...plan, steps: plan.steps.map((s) => ({ ...s })) });
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
      if (this.online) {
        await this.world.reportFired(alarm.taskId, {
          planVersion: alarm.version,
          seq: alarm.seq,
          firedAt: now,
          level: rang,
          permissionMissing: degrade,
        });
      }
    }
  }

  /** Done, Snooze and Can't today stop local alarms first, even offline (ADR 0005). */
  stopLocal(taskId: TaskId): void {
    this.alarms.delete(taskId);
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

export class Client {
  constructor(
    private readonly world: World,
    readonly person: Person,
  ) {}

  get id(): PersonId {
    return this.person.id;
  }

  get phone(): SimPhone {
    return this.world.phoneOf(this.id);
  }

  /** Write free text in the one input box. */
  async write(text: string): Promise<WriteResult> {
    return this.world.write(this.id, text);
  }

  async command(
    taskId: TaskId,
    cmd: Command,
  ): Promise<{ ok: true } | { ok: false; error: DomainError }> {
    if (cmd.type === "done" || cmd.type === "cant_today" || cmd.type === "photo_sent") {
      this.phone.stopLocal(taskId);
    }
    return this.world.command(this.id, taskId, cmd);
  }

  async sendPhoto(taskId: TaskId): Promise<{ ok: true } | { ok: false; error: DomainError }> {
    const { path } = await this.world.photos.createUploadUrl(taskId);
    return this.command(taskId, { type: "photo_sent", photoPath: path });
  }

  async invite(phone: string, relationship: EdgeType, prefilledName: string): Promise<string> {
    return this.world.invite(this.id, phone, relationship, prefilledName);
  }

  async acceptInvite(code: string, relationship: EdgeType): Promise<void> {
    await this.world.acceptInvite(this.id, code, relationship);
  }

  declineInvite(code: string): void {
    this.world.declineInvite(this.id, code);
  }

  logOut(): void {
    this.world.logOut(this.id);
  }

  async deleteAccount(): Promise<void> {
    await this.world.deleteAccount(this.id);
  }
}

export class World implements TickPort {
  readonly clock = new FakeClock(SCENARIO_START);
  readonly push = new RecordingPushSender(this.clock);
  readonly photos = new FakePhotoStorage();
  readonly extractor: FixtureExtractor;
  /** Device-side rule breaks (ringing in quiet hours). Scenarios assert this stays empty. */
  readonly violations: string[] = [];

  private readonly people = new Map<PersonId, Person>();
  private edges: Edge[] = [];
  private readonly invites = new Map<string, Invite>();
  private readonly tasks = new Map<TaskId, Task>();
  readonly events: TaskEvent[] = [];
  private readonly plans = new Map<TaskId, PokePlan[]>();
  private readonly actions: (ClaimedAction & { doneAt: Date | null })[] = [];
  private readonly phones = new Map<PersonId, SimPhone>();
  private nextId = 1;

  constructor(fixtures: Record<string, Extraction> = {}) {
    this.extractor = new FixtureExtractor(new Map(Object.entries(fixtures)));
  }

  // ---- setup ---------------------------------------------------------------------------------

  /** A signed-up person with one phone and every permission granted. */
  addPerson(name: string, permissions: PermissionSummary = allPermissions): Client {
    const person = aPerson(name);
    this.people.set(person.id, person);
    const device: Device = {
      id: `${person.id}-phone`,
      personId: person.id,
      fcmToken: `token-${person.id}`,
      permissions,
      lastSeenAt: this.clock.now(),
    };
    this.phones.set(person.id, new SimPhone(this, device));
    return new Client(this, person);
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

  // ---- reads ---------------------------------------------------------------------------------

  person(id: PersonId): Person {
    const p = this.people.get(id);
    if (!p) throw new Error(`no person ${id}`);
    return p;
  }

  task(id: TaskId): Task {
    const t = this.tasks.get(id);
    if (!t) throw new Error(`no task ${id}`);
    return t;
  }

  phoneOf(id: PersonId): SimPhone {
    const p = this.phones.get(id);
    if (!p) throw new Error(`no phone for ${id}`);
    return p;
  }

  currentPlan(taskId: TaskId): PokePlan | undefined {
    return this.plans.get(taskId)?.at(-1);
  }

  plansOf(taskId: TaskId): PokePlan[] {
    return this.plans.get(taskId) ?? [];
  }

  eventsOf(taskId: TaskId): TaskEvent[] {
    return this.events.filter((e) => e.taskId === taskId);
  }

  tasksReceivedBy(id: PersonId): Task[] {
    return [...this.tasks.values()].filter((t) => t.recipientId === id);
  }

  edgesOf(id: PersonId): Edge[] {
    return this.edges.filter((e) => e.fromPersonId === id || e.toPersonId === id);
  }

  reachabilityOf(id: PersonId): Reachability {
    return deriveReachability(this.devicesOf(id));
  }

  /** Invites as the inviter is allowed to see them. */
  invitesSeenBy(inviterId: PersonId): { code: string; status: string }[] {
    return [...this.invites.values()]
      .filter((i) => i.inviterId === inviterId)
      .map((i) => ({ code: i.code, status: inviteStatusForInviter(i) }));
  }

  pendingActions(taskId?: TaskId): ClaimedAction[] {
    return this.actions.filter(
      (a) => a.doneAt === null && (taskId === undefined || a.taskId === taskId),
    );
  }

  private devicesOf(id: PersonId): Device[] {
    const phone = this.phones.get(id);
    return phone ? [phone.device] : [];
  }

  private taskContext(task: Task): TaskContext {
    return {
      sender: this.person(task.senderId),
      recipient: this.person(task.recipientId),
      reachability: this.reachabilityOf(task.recipientId),
      history: this.eventsOf(task.id),
      now: this.clock.now(),
    };
  }

  // ---- commands (what the API does in stage 4) ----------------------------------------------

  async write(senderId: PersonId, text: string): Promise<WriteResult> {
    const sender = this.person(senderId);
    const now = this.clock.now();
    const connections = this.edges
      .filter((e) => e.fromPersonId === senderId && e.status === "active")
      .map((e) => ({
        personId: e.toPersonId,
        name: this.person(e.toPersonId).displayName,
        relationship: e.type,
      }));
    const extraction = await this.extractor.extract({
      text,
      now,
      timeZone: sender.quietHours.timeZone,
      senderName: sender.displayName,
      connections: connections.map(({ name, relationship }) => ({ name, relationship })),
    });
    const resolved = resolveExtraction(extraction, sender, connections, now);
    if (resolved.kind === "ask") return resolved;

    const recipient = this.person(resolved.recipientId);
    const r = createTask({
      id: `task-${String(this.nextId++)}`,
      sender,
      recipient,
      edge:
        this.edges.find((e) => e.fromPersonId === senderId && e.toPersonId === recipient.id) ??
        null,
      title: resolved.title,
      detail: resolved.detail,
      deadline: resolved.deadline,
      finishMode: resolved.finishMode,
      rawText: text,
      reachability: this.reachabilityOf(recipient.id),
      now,
    });
    if (!r.ok) return { kind: "rejected", error: r.error };
    await this.applyOutcome(r.value);
    return { kind: "created", taskId: r.value.task.id };
  }

  async command(
    actorId: PersonId,
    taskId: TaskId,
    cmd: Command,
  ): Promise<{ ok: true } | { ok: false; error: DomainError }> {
    const task = this.task(taskId);
    const r = applyCommand(task, cmd, actorId, this.taskContext(task));
    if (!r.ok) return r;
    await this.applyOutcome(r.value);
    return { ok: true };
  }

  async reportFired(taskId: TaskId, report: FiredReport): Promise<void> {
    const task = this.task(taskId);
    const plan = this.currentPlan(taskId) ?? { taskId, version: 0, steps: [] };
    const result = reportFired({ task, plan }, report, this.clock.now());
    const step = plan.steps.find((s) => s.seq === result.firedSeq);
    if (step) step.firedAt = report.firedAt;
    this.events.push(...result.events);
    await this.sendPushes(result.pushes);
  }

  async invite(
    inviterId: PersonId,
    phone: string,
    relationship: EdgeType,
    prefilledName: string,
  ): Promise<string> {
    const code = `code-${String(this.nextId++)}`;
    const r = createInvite(
      { id: code, inviterId, inviteePhone: phone, relationship, prefilledName, code },
      this.person(inviterId),
    );
    if (!r.ok) throw new Error(r.error);
    this.invites.set(code, r.value);
    return Promise.resolve(code);
  }

  async acceptInvite(inviteeId: PersonId, code: string, relationship: EdgeType): Promise<void> {
    const invite = this.invites.get(code);
    if (!invite) throw new Error(`no invite ${code}`);
    const r = acceptInvite(invite, this.person(inviteeId), relationship);
    if (!r.ok) throw new Error(r.error);
    this.invites.set(code, r.value.invite);
    this.edges.push(...r.value.edges);
    await this.sendPushes(r.value.pushes);
  }

  declineInvite(inviteeId: PersonId, code: string): void {
    const invite = this.invites.get(code);
    if (!invite) throw new Error(`no invite ${code}`);
    const r = declineInvite(invite, this.person(inviteeId));
    if (!r.ok) throw new Error(r.error);
    this.invites.set(code, r.value);
  }

  logOut(personId: PersonId): void {
    const phone = this.phoneOf(personId);
    phone.device = logOut(phone.device);
  }

  async deleteAccount(personId: PersonId): Promise<void> {
    const person = this.person(personId);
    const open = [...this.tasks.values()].filter(
      (t) =>
        (t.senderId === personId || t.recipientId === personId) &&
        t.status !== "DONE" &&
        t.status !== "CANCELLED",
    );
    const r = deleteAccount(
      person,
      this.edges,
      open.map((task) => ({ task, ctx: this.taskContext(task) })),
      this.clock.now(),
    );
    this.people.set(personId, r.person);
    this.edges = this.edges.map(
      (e) =>
        r.edges.find((x) => x.fromPersonId === e.fromPersonId && x.toPersonId === e.toPersonId) ??
        e,
    );
    for (const o of r.cancelled) await this.applyOutcome(o);
    this.logOut(personId);
  }

  /** Persist an outcome as one transaction would, then send its pushes. */
  private async applyOutcome(o: Outcome): Promise<void> {
    const now = this.clock.now();
    this.tasks.set(o.task.id, o.task);
    this.events.push(...o.events);
    this.plans.set(o.task.id, [...this.plansOf(o.task.id), o.plan]);
    for (const a of this.actions) {
      if (a.taskId === o.task.id && a.doneAt === null) a.doneAt = now;
    }
    for (const a of o.scheduledActions) {
      this.actions.push({ ...a, id: `action-${String(this.nextId++)}`, doneAt: null });
    }
    if (o.deletePhotos) await this.photos.deleteForTask(o.task.id);
    await this.sendPushes(o.pushes);
  }

  private async sendPushes(pushes: readonly PushMessage[]): Promise<void> {
    for (const p of pushes) {
      await this.push.send(p);
      this.phones.get(p.to)?.receivePush(p);
    }
  }

  // ---- TickPort ------------------------------------------------------------------------------

  claimDue(now: Date): Promise<ClaimedAction[]> {
    return Promise.resolve(
      this.actions.filter((a) => a.doneAt === null && a.dueAt.getTime() <= now.getTime()),
    );
  }

  loadContext(action: ClaimedAction, now: Date): Promise<ActionContext | null> {
    if (action.taskId === null) return Promise.resolve(null);
    const task = this.task(action.taskId);
    const plan = this.currentPlan(task.id);
    if (!plan) return Promise.resolve(null);
    return Promise.resolve({
      sender: this.person(task.senderId),
      recipient: this.person(task.recipientId),
      history: this.eventsOf(task.id),
      now,
      task,
      plan,
      recipientDevices: this.devicesOf(task.recipientId),
    });
  }

  async complete(action: ClaimedAction, result: ActionResult): Promise<void> {
    const stored = this.actions.find((a) => a.id === action.id);
    if (stored) stored.doneAt = this.clock.now();
    switch (result.kind) {
      case "transition":
        await this.applyOutcome(result.outcome);
        return;
      case "notify":
        this.events.push(...result.events);
        await this.sendPushes(result.pushes);
        return;
      case "noop":
        return;
    }
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

  private async runDue(): Promise<void> {
    const now = this.clock.now();
    for (const phone of this.phones.values()) await phone.ringDue(now);
    await tick(now, this);
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
