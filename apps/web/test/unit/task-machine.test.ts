import { describe, expect, it } from "vitest";
import {
  AUTO_APPROVE_AFTER_MS,
  applyCommand,
  PHOTO_CLEANUP_RETRY_MS,
  createTask,
  removeMissedCard,
  type Command,
  type TaskContext,
} from "../../src/domain/task-machine";
import { addMs, HOUR } from "../../src/domain/time";
import type { Edge, Task, TaskEvent, TaskStatus } from "../../src/domain/types";
import { aPerson, aTask } from "../builders";

const sibel = aPerson("Sibel");
const eren = aPerson("Eren");
const now = new Date("2026-10-11T20:00:00+03:00");
const edge: Edge = {
  fromPersonId: "sibel",
  toPersonId: "eren",
  type: "spouse",
  status: "active",
  notifyMe: true,
};

function ctx(history: TaskEvent[] = [], at: Date = now): TaskContext {
  return { sender: sibel, recipient: eren, reachability: "FULL_SCREEN", history, now: at };
}

function apply(task: Task, cmd: Command, actor: string | null, c: TaskContext = ctx()) {
  const r = applyCommand(task, cmd, actor, c);
  if (!r.ok) throw new Error(`expected ok, got ${r.error}`);
  return r.value;
}

const commands: { cmd: Command; actor: string | null }[] = [
  { cmd: { type: "done", cameraUnavailable: true }, actor: "eren" },
  { cmd: { type: "photo_sent", photoPath: "p.jpg" }, actor: "eren" },
  { cmd: { type: "cant_today", note: null }, actor: "eren" },
  { cmd: { type: "did_it_later" }, actor: "eren" },
  { cmd: { type: "cancel", note: null }, actor: "sibel" },
  { cmd: { type: "edit", detail: "2.000 TL" }, actor: "sibel" },
  { cmd: { type: "approve" }, actor: "sibel" },
  { cmd: { type: "re_request", note: null }, actor: "sibel" },
  { cmd: { type: "new_deadline", deadline: addMs(now, 5 * HOUR) }, actor: "sibel" },
  { cmd: { type: "sender_handles" }, actor: "sibel" },
  { cmd: { type: "deadline_passed" }, actor: null },
  { cmd: { type: "approval_timeout" }, actor: null },
  { cmd: { type: "account_deleted", personId: "eren" }, actor: null },
  { cmd: { type: "connection_removed", personId: "sibel" }, actor: null },
  { cmd: { type: "replan" }, actor: null },
];

// The table in docs/domain.md, Task states. Anything not listed must be rejected.
const allowed: Record<TaskStatus, Partial<Record<Command["type"], TaskStatus>>> = {
  SCHEDULED: {
    done: "DONE",
    photo_sent: "AWAITING_APPROVAL",
    cant_today: "DROPPED",
    deadline_passed: "MISSED",
    cancel: "CANCELLED",
    edit: "SCHEDULED",
    account_deleted: "CANCELLED",
    connection_removed: "CANCELLED",
    replan: "SCHEDULED",
  },
  AWAITING_APPROVAL: {
    approve: "DONE",
    approval_timeout: "DONE",
    re_request: "REJECTED",
    cancel: "CANCELLED",
    account_deleted: "CANCELLED",
    connection_removed: "CANCELLED",
  },
  REJECTED: {
    photo_sent: "AWAITING_APPROVAL",
    cant_today: "DROPPED",
    deadline_passed: "MISSED",
    cancel: "CANCELLED",
    account_deleted: "CANCELLED",
    connection_removed: "CANCELLED",
    replan: "REJECTED",
  },
  MISSED: {
    did_it_later: "DONE",
    new_deadline: "SCHEDULED",
    cancel: "CANCELLED",
    account_deleted: "CANCELLED",
    connection_removed: "CANCELLED",
  },
  DROPPED: {
    new_deadline: "SCHEDULED",
    sender_handles: "CANCELLED",
    account_deleted: "CANCELLED",
    connection_removed: "CANCELLED",
  },
  DONE: {},
  CANCELLED: {},
};

describe("task state machine", () => {
  for (const [status, row] of Object.entries(allowed) as [
    TaskStatus,
    (typeof allowed)[TaskStatus],
  ][]) {
    for (const { cmd, actor } of commands) {
      const target = row[cmd.type];
      it(`${status} + ${cmd.type} -> ${target ?? "rejected"}`, () => {
        const task = aTask().withStatus(status).build();
        const r = applyCommand(task, cmd, actor, ctx());
        if (target === undefined) {
          expect(r).toEqual({ ok: false, error: "wrong_status" });
        } else {
          expect(r.ok && r.value.task.status).toBe(target);
        }
      });
    }
  }

  it("bumps the plan version and appends an event on every transition", () => {
    const task = aTask().build();
    const o = apply(task, { type: "cant_today", note: "Yarın yaparım" }, "eren");
    expect(o.task.planVersion).toBe(task.planVersion + 1);
    expect(o.plan).toEqual({ taskId: task.id, version: o.task.planVersion, steps: [] });
    expect(o.events.map((e) => e.type)).toEqual(["dropped"]);
    expect(o.events[0]?.payload).toEqual({ note: "Yarın yaparım" });
  });

  it("checks who may act", () => {
    const task = aTask().build();
    expect(applyCommand(task, { type: "cant_today", note: null }, "sibel", ctx())).toEqual({
      ok: false,
      error: "not_recipient",
    });
    expect(applyCommand(task, { type: "cancel", note: null }, "eren", ctx())).toEqual({
      ok: false,
      error: "not_sender",
    });
  });

  it("a missed task takes a photo that was taken before the deadline and uploaded late (6e)", () => {
    const task = aTask().withStatus("MISSED").withFinishMode("photo").build();
    const before = addMs(task.deadline, -60_000);
    const late = apply(task, { type: "photo_sent", photoPath: "p", takenAt: before }, "eren", {
      ...ctx(),
      now: addMs(task.deadline, HOUR),
    });
    expect(late.task.status).toBe("AWAITING_APPROVAL");

    const c = { ...ctx(), now: addMs(task.deadline, HOUR) };
    const after = addMs(task.deadline, 60_000);
    expect(
      applyCommand(task, { type: "photo_sent", photoPath: "p", takenAt: after }, "eren", c),
    ).toEqual({
      ok: false,
      error: "wrong_status",
    });
    // A device clock ahead of the server cannot make a photo count.
    const future = addMs(c.now, 60_000);
    expect(
      applyCommand(
        { ...task, deadline: addMs(c.now, HOUR) },
        { type: "photo_sent", photoPath: "p", takenAt: future },
        "eren",
        c,
      ),
    ).toEqual({ ok: false, error: "wrong_status" });
  });

  it("closing a photo task plans a later photo cleanup (NUD-25); a Bitti task does not", () => {
    const photo = aTask().withStatus("AWAITING_APPROVAL").withFinishMode("photo").build();
    const approved = apply(photo, { type: "approve" }, "sibel");
    expect(approved.deletePhotos).toBe(true);
    expect(approved.scheduledActions).toEqual([
      {
        kind: "photo_cleanup",
        taskId: photo.id,
        dueAt: addMs(ctx().now, PHOTO_CLEANUP_RETRY_MS),
        payload: {},
      },
    ]);

    const done = apply(aTask().withFinishMode("done").build(), { type: "done" }, "eren");
    expect(done.scheduledActions).toEqual([]);
  });

  it("a missed task takes a Bitti tapped offline before the deadline (6a)", () => {
    const task = aTask().withStatus("MISSED").withFinishMode("done").build();
    const c = { ...ctx(), now: addMs(task.deadline, HOUR) };
    const late = apply(task, { type: "done", doneAt: addMs(task.deadline, -60_000) }, "eren", c);
    expect(late.task).toMatchObject({ status: "DONE", late: false });
    expect(late.events.map((e) => e.type)).toEqual(["done"]);

    expect(
      applyCommand(task, { type: "done", doneAt: addMs(task.deadline, 60_000) }, "eren", c),
    ).toEqual({ ok: false, error: "wrong_status" });
  });

  it("requires a photo for a photo task unless the camera is unavailable", () => {
    const task = aTask().withFinishMode("photo").build();
    expect(applyCommand(task, { type: "done" }, "eren", ctx())).toEqual({
      ok: false,
      error: "wrong_finish_mode",
    });
    expect(apply(task, { type: "done", cameraUnavailable: true }, "eren").task.status).toBe("DONE");
  });

  it("rejects a photo on a done-mode task", () => {
    const task = aTask().withFinishMode("done").build();
    expect(applyCommand(task, { type: "photo_sent", photoPath: "p" }, "eren", ctx())).toEqual({
      ok: false,
      error: "wrong_finish_mode",
    });
  });

  it("closes a photo task to self without approval", () => {
    const task = aTask().from(sibel).forRecipient(sibel).build();
    const o = apply(task, { type: "photo_sent", photoPath: "p" }, "sibel", {
      ...ctx(),
      recipient: sibel,
    });
    expect(o.task.status).toBe("DONE");
    expect(o.events.map((e) => e.type)).toEqual(["photo_sent", "done"]);
    expect(o.deletePhotos).toBe(true);
    // A silent sync for the person's other phones (NUD-59).
    expect(o.pushes).toEqual([
      { to: "sibel", hint: "sync", taskId: "task-1", event: "photo_sent" },
    ]);
  });

  it("schedules auto-approve three hours after the photo", () => {
    const o = apply(aTask().build(), { type: "photo_sent", photoPath: "p" }, "eren");
    expect(o.scheduledActions).toEqual([
      {
        kind: "approval_timeout",
        taskId: "task-1",
        dueAt: addMs(now, AUTO_APPROVE_AFTER_MS),
        payload: { planVersion: 2 },
      },
    ]);
    expect(o.plan.steps).toEqual([]);
    expect(o.pushes).toEqual([
      { to: "sibel", hint: "sync", taskId: "task-1", event: "photo_sent" },
      { to: "eren", hint: "sync", taskId: "task-1", event: "photo_sent" },
    ]);
  });

  it("marks 'Sonradan yaptım' as late", () => {
    const o = apply(aTask().withStatus("MISSED").build(), { type: "did_it_later" }, "eren");
    expect(o.task).toMatchObject({ status: "DONE", late: true });
  });

  it("closes 'Ben hallederim' as cancelled with reason sender_handles", () => {
    const o = apply(aTask().withStatus("DROPPED").build(), { type: "sender_handles" }, "sibel");
    expect(o.events[0]?.payload).toEqual({ reason: "sender_handles" });
    expect(o.pushes).toEqual([
      { to: "eren", hint: "task_cancelled", taskId: "task-1", event: "cancelled" },
      { to: "sibel", hint: "sync", taskId: "task-1", event: "cancelled" },
    ]);
  });

  it("tells both phones to sync when a task is done without a photo, with no notification (NUD-59)", () => {
    const task = aTask().withFinishMode("done").build();
    expect(apply(task, { type: "done" }, "eren").pushes).toEqual([
      { to: "sibel", hint: "sync", taskId: "task-1", event: "done" },
      { to: "eren", hint: "sync", taskId: "task-1", event: "done" },
    ]);
  });

  it("regenerates the plan on a deadline edit", () => {
    const task = aTask().withDeadline("2026-10-12T09:00:00+03:00").build();
    const o = apply(
      task,
      { type: "edit", deadline: new Date("2026-10-12T18:00:00+03:00") },
      "sibel",
    );
    expect(o.task.deadline).toEqual(new Date("2026-10-12T18:00:00+03:00"));
    expect(o.plan.version).toBe(2);
    expect(o.plan.steps[0]?.plannedAt).toEqual(new Date("2026-10-12T17:00:00+03:00"));
    expect(o.events.map((e) => e.type)).toEqual(["edited", "plan_generated"]);
    expect(o.scheduledActions.find((a) => a.kind === "deadline")?.dueAt).toEqual(
      new Date("2026-10-12T18:00:00+03:00"),
    );
  });

  it("moves an at task's moment on a time edit and keeps its grace (ADR 0017)", () => {
    const task = aTask().at("2026-10-12T09:00:00+03:00", 30).build();
    const o = apply(
      task,
      { type: "edit", deadline: new Date("2026-10-12T18:00:00+03:00") },
      "sibel",
    );
    expect(o.task.remindAt).toEqual(new Date("2026-10-12T18:00:00+03:00"));
    expect(o.task.deadline).toEqual(new Date("2026-10-12T18:30:00+03:00"));
    expect(o.plan.steps[0]?.plannedAt).toEqual(new Date("2026-10-12T18:00:00+03:00"));
  });

  it("rejects a deadline in the past", () => {
    const task = aTask().build();
    expect(
      applyCommand(task, { type: "edit", deadline: addMs(now, -HOUR) }, "sibel", ctx()),
    ).toEqual({ ok: false, error: "deadline_not_in_future" });
  });

  it("resumes at the level that already fired after a re-request", () => {
    const fired: TaskEvent = {
      taskId: "task-1",
      actorId: "eren",
      type: "poke_fired",
      payload: { seq: 2, level: "SOUND" },
      at: now,
    };
    const task = aTask().withStatus("AWAITING_APPROVAL").build();
    const o = apply(task, { type: "re_request", note: "Bulanık" }, "sibel", ctx([fired]));
    expect(o.task.status).toBe("REJECTED");
    expect(o.plan.steps.map((s) => s.level)).toEqual(["SOUND", "FULL_SCREEN", "HUMAN"]);
    expect(o.pushes).toEqual([
      { to: "eren", hint: "plan_changed", taskId: "task-1", event: "re_requested" },
      { to: "sibel", hint: "sync", taskId: "task-1", event: "re_requested" },
    ]);
  });

  it("tells the other side when an account deletion cancels a task", () => {
    const o = apply(aTask().build(), { type: "account_deleted", personId: "eren" }, null);
    expect(o.task.status).toBe("CANCELLED");
    expect(o.events[0]).toMatchObject({ actorId: "eren", payload: { reason: "account_deleted" } });
    expect(o.pushes).toEqual([
      { to: "sibel", hint: "task_cancelled", taskId: "task-1", event: "cancelled" },
      { to: "eren", hint: "sync", taskId: "task-1", event: "cancelled" },
    ]);
  });

  it("cancels without a notification when a connection is removed (5b)", () => {
    const o = apply(aTask().build(), { type: "connection_removed", personId: "sibel" }, null);
    expect(o.task.status).toBe("CANCELLED");
    expect(o.events[0]).toMatchObject({
      actorId: "sibel",
      payload: { reason: "connection_removed" },
    });
    expect(o.plan.steps).toEqual([]);
    expect(o.pushes).toEqual([
      { to: "sibel", hint: "sync", taskId: "task-1", event: "cancelled" },
      { to: "eren", hint: "sync", taskId: "task-1", event: "cancelled" },
    ]);
  });

  it("refuses to cancel for a removal by someone outside the task", () => {
    const r = applyCommand(
      aTask().build(),
      { type: "connection_removed", personId: "ayse" },
      null,
      ctx(),
    );
    expect(r).toEqual({ ok: false, error: "not_sender" });
  });

  it("replans from the level reached when a setting changes, keeping the status", () => {
    const fired: TaskEvent = {
      taskId: "task-1",
      actorId: "eren",
      type: "poke_fired",
      payload: { seq: 2, level: "SOUND" },
      at: now,
    };
    const task = aTask().build();
    const o = apply(task, { type: "replan" }, null, {
      ...ctx([fired]),
      senderEdge: { ...edge, notifyMe: false },
    });
    expect(o.task.status).toBe("SCHEDULED");
    expect(o.task.statusChangedAt).toEqual(task.statusChangedAt);
    expect(o.task.planVersion).toBe(task.planVersion + 1);
    expect(o.plan.steps.map((s) => s.level)).toEqual(["SOUND", "FULL_SCREEN"]);
    expect(o.events.map((e) => e.type)).toEqual(["plan_generated"]);
    expect(o.pushes).toEqual([
      { to: "eren", hint: "plan_changed", taskId: "task-1", event: "plan_generated" },
    ]);
  });
});

describe("snooze (open-questions#1: 2 snoozes, 15 then 10 minutes, never past the deadline)", () => {
  const at = new Date("2026-10-11T22:40:00+03:00");
  const fired: TaskEvent = {
    taskId: "task-1",
    actorId: "eren",
    type: "poke_fired",
    payload: { seq: 2, level: "SOUND" },
    at: new Date("2026-10-11T22:30:00+03:00"),
  };
  const snoozed = (minutes: number): TaskEvent => ({
    taskId: "task-1",
    actorId: "eren",
    type: "snoozed",
    payload: { minutes },
    at,
  });

  it("first snooze: 15 minutes, the rest of the plan starts when it ends at the level reached", () => {
    const task = aTask().build();
    const o = apply(task, { type: "snooze" }, "eren", ctx([fired], at));
    expect(o.task.status).toBe("SCHEDULED");
    expect(o.task.planVersion).toBe(task.planVersion + 1);
    expect(o.events[0]).toMatchObject({
      type: "snoozed",
      payload: { count: 1, minutes: 15, until: "2026-10-11T19:55:00.000Z" },
    });
    const until = new Date("2026-10-11T22:55:00+03:00").getTime();
    expect(o.plan.steps.length).toBeGreaterThan(0);
    for (const step of o.plan.steps) expect(step.plannedAt.getTime()).toBeGreaterThanOrEqual(until);
    expect(o.plan.steps[0]?.level).toBe("SOUND");
    expect(o.pushes).toEqual([
      { to: "eren", hint: "plan_changed", taskId: "task-1", event: "snoozed" },
      { to: "sibel", hint: "sync", taskId: "task-1", event: "snoozed" },
    ]);
  });

  it("second snooze is 10 minutes, a third is refused", () => {
    const task = aTask().build();
    const second = apply(task, { type: "snooze" }, "eren", ctx([fired, snoozed(15)], at));
    expect(second.events[0]?.payload).toMatchObject({ count: 2, minutes: 10 });
    expect(
      applyCommand(task, { type: "snooze" }, "eren", ctx([fired, snoozed(15), snoozed(10)], at)),
    ).toEqual({ ok: false, error: "snooze_not_allowed" });
  });

  it("is refused when it would run past the deadline", () => {
    const task = aTask()
      .withDeadline(addMs(at, 10 * 60_000))
      .build();
    expect(applyCommand(task, { type: "snooze" }, "eren", ctx([fired], at))).toEqual({
      ok: false,
      error: "snooze_not_allowed",
    });
  });

  it("only the recipient snoozes, and only an open task", () => {
    expect(applyCommand(aTask().build(), { type: "snooze" }, "sibel", ctx([], at))).toEqual({
      ok: false,
      error: "not_recipient",
    });
    expect(
      applyCommand(
        aTask().withStatus("AWAITING_APPROVAL").build(),
        { type: "snooze" },
        "eren",
        ctx([], at),
      ),
    ).toEqual({ ok: false, error: "wrong_status" });
    expect(
      apply(aTask().withStatus("REJECTED").build(), { type: "snooze" }, "eren", ctx([], at)).task
        .status,
    ).toBe("REJECTED");
  });
});

describe("createTask", () => {
  const input = {
    id: "task-1",
    sender: sibel,
    recipient: eren,
    edge,
    title: "Temizlikçi için para çek",
    detail: "1.000 TL",
    deadline: new Date("2026-10-12T09:00:00+03:00"),
    finishMode: "photo" as const,
    rawText: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
    reachability: "FULL_SCREEN" as const,
    now,
  };

  it("creates a scheduled task with its first plan and timers", () => {
    const r = createTask(input);
    if (!r.ok) throw new Error(r.error);
    const o = r.value;
    expect(o.task).toMatchObject({ status: "SCHEDULED", planVersion: 1 });
    expect(o.events.map((e) => e.type)).toEqual(["created", "plan_generated"]);
    expect(o.plan.steps).toHaveLength(4);
    expect(o.scheduledActions.map((a) => a.kind)).toEqual([
      "deadline",
      "lost_poke_check",
      "lost_poke_check",
      "lost_poke_check",
      "human_check",
    ]);
    expect(o.pushes).toEqual([
      { to: "eren", hint: "plan_changed", taskId: "task-1", event: "created" },
      { to: "sibel", hint: "sync", taskId: "task-1", event: "created" },
    ]);
  });

  it("creates an at task: the moment, the deadline after its grace, no poke before the moment", () => {
    const r = createTask({ ...input, timing: "at", missAfterMinutes: 120 });
    if (!r.ok) throw new Error(r.error);
    const o = r.value;
    expect(o.task).toMatchObject({
      timing: "at",
      remindAt: new Date("2026-10-12T09:00:00+03:00"),
      deadline: new Date("2026-10-12T11:00:00+03:00"),
    });
    expect(o.plan.steps[0]).toMatchObject({
      level: "SOUND",
      plannedAt: new Date("2026-10-12T09:00:00+03:00"),
    });
    expect(o.scheduledActions.find((a) => a.kind === "deadline")?.dueAt).toEqual(
      new Date("2026-10-12T11:00:00+03:00"),
    );
  });

  it("refuses a recipient who is not connected (pending invite)", () => {
    expect(createTask({ ...input, edge: null })).toEqual({ ok: false, error: "not_connected" });
    expect(createTask({ ...input, edge: { ...edge, status: "pending" } })).toEqual({
      ok: false,
      error: "not_connected",
    });
  });

  it("allows a task to self without a connection", () => {
    const r = createTask({ ...input, recipient: sibel, edge: null });
    expect(r.ok).toBe(true);
  });

  it("refuses a deleted recipient", () => {
    const r = createTask({ ...input, recipient: { ...eren, deletedAt: now } });
    expect(r).toEqual({ ok: false, error: "recipient_unavailable" });
  });
});

describe("removeMissedCard (NUD-49)", () => {
  it("only the recipient, only a missed task", () => {
    const missed = aTask().withStatus("MISSED").build();
    const r = removeMissedCard(missed, "eren", now);
    expect(r).toEqual({ ok: true, value: { ...missed, removedByRecipientAt: now } });
    expect(removeMissedCard(missed, "sibel", now)).toEqual({ ok: false, error: "not_recipient" });
    expect(removeMissedCard(aTask().build(), "eren", now)).toEqual({
      ok: false,
      error: "wrong_status",
    });
  });
});
