import { describe, expect, it } from "vitest";
import {
  AUTO_APPROVE_AFTER_MS,
  applyCommand,
  PHOTO_CLEANUP_RETRY_MS,
  createTask,
  removeMissedCard,
  setExamplePhoto,
  acknowledge,
  type Command,
  type TaskContext,
} from "../../src/domain/task-machine";
import { addMs, HOUR, MINUTE } from "../../src/domain/time";
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
  { cmd: { type: "reassign", recipientId: "sibel" }, actor: "sibel" },
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
    reassign: "CANCELLED",
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

  it("Kime changed: closes as reassigned, naming the new recipient, and tells the old one (NUD-114)", () => {
    const o = apply(aTask().build(), { type: "reassign", recipientId: "sibel" }, "sibel");
    expect(o.events[0]?.payload).toEqual({ reason: "reassigned", note: null, to: "sibel" });
    expect(o.plan.steps).toEqual([]);
    expect(o.pushes).toContainEqual(
      expect.objectContaining({ to: "eren", hint: "task_cancelled" }),
    );
    // The same recipient again is not a change.
    expect(
      applyCommand(aTask().build(), { type: "reassign", recipientId: "eren" }, "sibel", ctx()),
    ).toEqual({ ok: false, error: "wrong_status" });
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

describe("snooze (open-questions#1: 2 snoozes, 15 then 10 minutes, 5 left before the deadline)", () => {
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

  it("is shortened so 5 minutes remain before the deadline (NUD-63)", () => {
    const task = aTask()
      .withDeadline(addMs(at, 14 * 60_000 + 30_000))
      .build();
    const o = apply(task, { type: "snooze" }, "eren", ctx([fired], at));
    expect(o.events[0]?.payload).toMatchObject({ count: 1, minutes: 9 });
  });

  it("ends before the recipient's quiet hours start, where it could not ring (NUD-163)", () => {
    // Quiet hours 23:00-07:00, a `by` task due 23:30.
    const task = aTask().withDeadline(new Date("2026-10-11T23:30:00+03:00")).build();
    const late = new Date("2026-10-11T22:50:00+03:00");
    const o = apply(task, { type: "snooze" }, "eren", ctx([fired], late));
    expect(o.events[0]?.payload).toMatchObject({ minutes: 9, until: "2026-10-11T19:59:00.000Z" });
    const tooLate = new Date("2026-10-11T22:56:00+03:00");
    expect(applyCommand(task, { type: "snooze" }, "eren", ctx([fired], tooLate))).toEqual({
      ok: false,
      error: "snooze_not_allowed",
    });
  });

  it("of an `at` task may end inside quiet hours, its own window rings there (open-questions#47)", () => {
    const task = aTask().at("2026-10-11T22:50:00+03:00", 120).build();
    const late = new Date("2026-10-11T22:55:00+03:00");
    const o = apply(task, { type: "snooze" }, "eren", ctx([fired], late));
    expect(o.events[0]?.payload).toMatchObject({ minutes: 15 });
  });

  it("is refused when less than 5 minutes would be left for it and 5 for the task", () => {
    const task = aTask()
      .withDeadline(addMs(at, 9 * 60_000 + 59_000))
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

  it("a by time a minute away is a moment: rings the at ladder, missed only after the grace (NUD-116)", () => {
    // Sema picked 16:58 at 16:57:44; the task was missed 15 seconds later after a single poke.
    const r = createTask({ ...input, deadline: addMs(now, 1 * MINUTE) });
    if (!r.ok) throw new Error(r.error);
    const o = r.value;
    expect(o.task).toMatchObject({
      timing: "at",
      remindAt: addMs(now, 1 * MINUTE),
      deadline: addMs(now, 61 * MINUTE),
    });
    expect(o.plan.steps.map((s) => s.level).slice(0, 3)).toEqual([
      "SOUND",
      "FULL_SCREEN",
      "FULL_SCREEN",
    ]);

    // Ten minutes away is still a deadline.
    const later = createTask({ ...input, deadline: addMs(now, 10 * MINUTE) });
    expect(later.ok && later.value.task).toMatchObject({ timing: "by", remindAt: null });
  });

  it("a new deadline two minutes away makes a by task a moment too (NUD-116)", () => {
    const o = apply(
      aTask().withStatus("MISSED").build(),
      { type: "new_deadline", deadline: addMs(now, 2 * MINUTE) },
      "sibel",
    );
    expect(o.task).toMatchObject({
      status: "SCHEDULED",
      timing: "at",
      remindAt: addMs(now, 2 * MINUTE),
      deadline: addMs(now, 62 * MINUTE),
    });
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

  it("takes now as the time of a one-time message without one, never a time before now (NUD-140)", () => {
    const notice = {
      ...input,
      timing: "at" as const,
      finishMode: "notice" as const,
      deadline: now,
    };
    const r = createTask(notice);
    if (!r.ok) throw new Error(r.error);
    expect(r.value.task.remindAt).toEqual(now);
    expect(r.value.plan.steps.map((s) => [s.level, s.plannedAt])).toEqual([["SOUND", now]]);
    expect(createTask({ ...notice, deadline: addMs(now, -MINUTE) })).toEqual({
      ok: false,
      error: "deadline_not_in_future",
    });
    expect(createTask({ ...input, deadline: now })).toEqual({
      ok: false,
      error: "deadline_not_in_future",
    });
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

describe("acknowledge (NUD-68)", () => {
  const none = new Set<TaskEvent["type"]>();

  it("reached logs delivered once and tells the sender; seen adds seen, and delivered if it is missing", () => {
    const task = aTask().build();
    const reached = acknowledge(task, "eren", false, none, now);
    expect(reached).toEqual({
      ok: true,
      value: {
        events: [{ taskId: task.id, actorId: "eren", type: "delivered", payload: {}, at: now }],
        pushes: [{ to: "sibel", hint: "sync", taskId: task.id, event: "delivered" }],
      },
    });
    const seenFirst = acknowledge(task, "eren", true, none, now);
    expect(seenFirst.ok && seenFirst.value.events.map((e) => e.type)).toEqual([
      "delivered",
      "seen",
    ]);
    const seenLater = acknowledge(task, "eren", true, new Set(["delivered"] as const), now);
    expect(seenLater.ok && seenLater.value.events.map((e) => e.type)).toEqual(["seen"]);
    expect(acknowledge(task, "eren", true, new Set(["delivered", "seen"] as const), now)).toEqual({
      ok: true,
      value: { events: [], pushes: [] },
    });
  });

  it("only the recipient; nothing for a closed task or a task to self", () => {
    expect(acknowledge(aTask().build(), "sibel", false, none, now)).toEqual({
      ok: false,
      error: "not_recipient",
    });
    const empty = { ok: true, value: { events: [], pushes: [] } };
    expect(acknowledge(aTask().withStatus("DONE").build(), "eren", true, none, now)).toEqual(empty);
    const self = { ...aTask().build(), senderId: "eren" };
    expect(acknowledge(self, "eren", true, none, now)).toEqual(empty);
  });
});

describe("example photo (NUD-138)", () => {
  const path = "tasks/task-1/example.jpg";

  it("the sender sets it on an open task; nothing else changes and the recipient syncs", () => {
    const task = aTask().withFinishMode("done").build();
    const r = setExamplePhoto(task, "sibel", path, now);
    expect(r).toEqual({
      ok: true,
      value: {
        task: { ...task, examplePhotoPath: path },
        events: [
          { taskId: "task-1", actorId: "sibel", type: "example_photo_set", payload: {}, at: now },
        ],
        pushes: [
          { to: "eren", hint: "sync", taskId: "task-1", event: "example_photo_set" },
          { to: "sibel", hint: "sync", taskId: "task-1", event: "example_photo_set" },
        ],
        replaced: null,
      },
    });
  });

  it("a second one replaces the first, the same path again is a no-op", () => {
    const task = aTask().withExamplePhoto("tasks/task-1/a.jpg").build();
    const r = setExamplePhoto(task, "sibel", "tasks/task-1/b.jpg", now);
    expect(r.ok && r.value.replaced).toBe("tasks/task-1/a.jpg");
    const same = setExamplePhoto(task, "sibel", "tasks/task-1/a.jpg", now);
    expect(same.ok && same.value.events).toEqual([]);
  });

  it("only the sender, only before the task closes", () => {
    expect(setExamplePhoto(aTask().build(), "eren", path, now)).toEqual({
      ok: false,
      error: "not_sender",
    });
    for (const status of ["DONE", "DROPPED", "MISSED", "CANCELLED"] as const) {
      expect(setExamplePhoto(aTask().withStatus(status).build(), "sibel", path, now)).toEqual({
        ok: false,
        error: "wrong_status",
      });
    }
    for (const status of ["REJECTED", "AWAITING_APPROVAL"] as const) {
      expect(setExamplePhoto(aTask().withStatus(status).build(), "sibel", path, now).ok).toBe(true);
    }
  });

  it("is never proof: Bitti on a done task closes it as before, a photo task still waits for the photo", () => {
    const done = apply(
      aTask().withFinishMode("done").withExamplePhoto().build(),
      { type: "done" },
      "eren",
    );
    expect(done.task.status).toBe("DONE");
    const photo = aTask().withExamplePhoto().build();
    expect(applyCommand(photo, { type: "done" }, "eren", ctx())).toEqual({
      ok: false,
      error: "wrong_finish_mode",
    });
  });

  it("goes with the folder when a done task closes: deleted, retried later, and the path cleared", () => {
    const task = aTask().withFinishMode("done").withExamplePhoto().build();
    for (const { cmd, actor } of [
      { cmd: { type: "done" } as Command, actor: "eren" },
      { cmd: { type: "cancel", note: null } as Command, actor: "sibel" },
      { cmd: { type: "deadline_passed" } as Command, actor: null },
      { cmd: { type: "account_deleted", personId: "sibel" } as Command, actor: null },
    ]) {
      const o = apply(task, cmd, actor);
      expect(o.deletePhotos).toBe(true);
      expect(o.task.examplePhotoPath).toBeNull();
      expect(o.scheduledActions.map((a) => a.kind)).toContain("photo_cleanup");
    }
    // Still open: the picture stays.
    const edited = apply(task, { type: "edit", detail: "2 kutu" }, "sibel");
    expect(edited.deletePhotos).toBe(false);
    expect(edited.task.examplePhotoPath).toBe(task.examplePhotoPath);
  });

  it("a done task without one deletes nothing, as before", () => {
    const o = apply(aTask().withFinishMode("done").build(), { type: "done" }, "eren");
    expect(o.deletePhotos).toBe(false);
    expect(o.scheduledActions).toEqual([]);
  });
});
