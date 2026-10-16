import { describe, expect, it } from "vitest";
import type { Device } from "../../src/domain/reachability";
import { MISSED_CARD_TTL_MS } from "../../src/domain/task-machine";
import {
  handleAction,
  reportFired,
  tick,
  visibleToRecipient,
  type ActionContext,
  type ActionResult,
  type ClaimedAction,
} from "../../src/domain/tick";
import { addMs, DAY, MINUTE } from "../../src/domain/time";
import type { PokePlan, ScheduledAction, Task } from "../../src/domain/types";
import { aPerson, allPermissions, aTask } from "../builders";

const now = new Date("2026-10-12T09:00:00+03:00");
const plannedAt = new Date("2026-10-12T08:30:00+03:00");

const phone: Device = {
  id: "eren-phone",
  personId: "eren",
  fid: "t",
  permissions: allPermissions,
  lastSeenAt: now,
};

function context(task: Task, overrides: Partial<ActionContext> = {}): ActionContext {
  const plan: PokePlan = {
    taskId: task.id,
    version: task.planVersion,
    steps: [{ seq: 2, plannedAt, level: "SOUND", firedAt: null }],
  };
  return {
    task,
    plan,
    sender: aPerson("Sibel"),
    recipient: aPerson("Eren"),
    history: [],
    now,
    recipientDevices: [phone],
    ...overrides,
  };
}

const action = (kind: ScheduledAction["kind"], payload: Record<string, unknown> = {}) => ({
  kind,
  taskId: "task-1",
  dueAt: now,
  payload: { planVersion: 1, ...payload },
});

describe("tick handlers", () => {
  it("deadline: SCHEDULED becomes MISSED", () => {
    const r = handleAction(action("deadline"), context(aTask().build()));
    expect(r.kind === "transition" && r.outcome.task.status).toBe("MISSED");
  });

  it("deadline: never MISSED while awaiting approval", () => {
    const task = aTask().withStatus("AWAITING_APPROVAL").build();
    expect(handleAction(action("deadline"), context(task))).toEqual({ kind: "noop" });
  });

  it("ignores actions from an older plan version", () => {
    const task = { ...aTask().build(), planVersion: 2 };
    expect(handleAction(action("deadline"), context(task))).toEqual({ kind: "noop" });
  });

  it("approval_timeout: auto-approves", () => {
    const task = aTask().withStatus("AWAITING_APPROVAL").build();
    const r = handleAction(action("approval_timeout"), context(task));
    expect(r.kind === "transition" && r.outcome.events[0]?.payload).toEqual({ auto: true });
  });

  it("human_check: tells the sender while the task is open", () => {
    const r = handleAction(action("human_check"), context(aTask().build()));
    expect(r).toMatchObject({
      kind: "notify",
      events: [{ type: "human_notified" }],
      pushes: [{ to: "sibel", event: "human_notified" }],
    });
    const done = aTask().withStatus("DONE").build();
    expect(handleAction(action("human_check"), context(done))).toEqual({ kind: "noop" });
  });

  it("lost_poke_check: lost when the device was online but sent no fired report", () => {
    const r = handleAction(action("lost_poke_check", { seq: 2 }), context(aTask().build()));
    expect(r).toMatchObject({ kind: "notify", events: [{ type: "poke_lost" }] });
  });

  it("lost_poke_check: not lost when the device was offline", () => {
    const offline = { ...phone, lastSeenAt: addMs(plannedAt, -MINUTE) };
    const r = handleAction(
      action("lost_poke_check", { seq: 2 }),
      context(aTask().build(), { recipientDevices: [offline] }),
    );
    expect(r).toEqual({ kind: "noop" });
  });

  it("lost_poke_check: not lost when the step fired", () => {
    const task = aTask().build();
    const ctx = context(task);
    const firedPlan = { ...ctx.plan, steps: ctx.plan.steps.map((s) => ({ ...s, firedAt: now })) };
    const r = handleAction(action("lost_poke_check", { seq: 2 }), { ...ctx, plan: firedPlan });
    expect(r).toEqual({ kind: "noop" });
  });

  it("missed_expiry: refreshes the recipient's list", () => {
    const task = aTask().withStatus("MISSED").build();
    expect(handleAction(action("missed_expiry"), context(task))).toEqual({
      kind: "notify",
      events: [],
      pushes: [{ to: "eren", hint: "sync", taskId: "task-1", event: "missed" }],
    });
  });

  it("photo_cleanup: deletes the photos of a closed task, never of one waiting for approval", () => {
    for (const status of ["DONE", "MISSED", "DROPPED", "CANCELLED"] as const) {
      expect(
        handleAction(action("photo_cleanup"), context(aTask().withStatus(status).build())),
      ).toEqual({
        kind: "delete_photos",
        taskId: "task-1",
      });
    }
    for (const status of ["AWAITING_APPROVAL", "SCHEDULED", "REJECTED"] as const) {
      expect(
        handleAction(action("photo_cleanup"), context(aTask().withStatus(status).build())),
      ).toEqual({
        kind: "noop",
      });
    }
  });

  it("permission_request_cooldown: nothing to do", () => {
    expect(handleAction(action("permission_request_cooldown"), context(aTask().build()))).toEqual({
      kind: "noop",
    });
  });
});

describe("tick(now)", () => {
  it("claims due actions, handles each and completes it", async () => {
    const claimed: ClaimedAction[] = [{ id: "a1", ...action("deadline") }];
    const completed: [string, ActionResult["kind"]][] = [];
    const n = await tick(now, {
      claimDue: () => Promise.resolve(claimed),
      loadContext: () => Promise.resolve(context(aTask().build())),
      complete: (a, r) => {
        completed.push([a.id, r.kind]);
        return Promise.resolve();
      },
    });
    expect(n).toBe(1);
    expect(completed).toEqual([["a1", "transition"]]);
  });

  it("completes an action whose task is gone as a no-op", async () => {
    const completed: ActionResult[] = [];
    await tick(now, {
      claimDue: () => Promise.resolve([{ id: "a1", ...action("deadline") }]),
      loadContext: () => Promise.resolve(null),
      complete: (_a, r) => {
        completed.push(r);
        return Promise.resolve();
      },
    });
    expect(completed).toEqual([{ kind: "noop" }]);
  });

  it("purges a deleted account without loading a task (NUD-89)", async () => {
    const completed: ActionResult[] = [];
    await tick(now, {
      claimDue: () =>
        Promise.resolve([
          {
            id: "a1",
            kind: "account_purge",
            taskId: null,
            dueAt: now,
            payload: { personId: "eren" },
          },
        ]),
      loadContext: () => Promise.reject(new Error("a purge has no task to load")),
      complete: (_a, r) => {
        completed.push(r);
        return Promise.resolve();
      },
    });
    expect(completed).toEqual([{ kind: "purge_account", personId: "eren" }]);
  });
});

describe("reportFired", () => {
  const task = aTask().build();
  const plan: PokePlan = {
    taskId: task.id,
    version: 1,
    steps: [{ seq: 3, plannedAt, level: "FULL_SCREEN", firedAt: null }],
  };

  it("records planned and actual time, and a missing permission", () => {
    const r = reportFired(
      { task, plan },
      { planVersion: 1, seq: 3, firedAt: now, level: "SOUND", permissionMissing: true },
      now,
    );
    expect(r.firedSeq).toBe(3);
    expect(r.events.map((e) => e.type)).toEqual(["poke_fired", "permission_missing"]);
    expect(r.events[0]?.payload).toMatchObject({
      level: "SOUND",
      planned: plannedAt.toISOString(),
      actual: now.toISOString(),
    });
  });

  it("answers a report for a cancelled task with a cancel", () => {
    const cancelled = aTask().withStatus("CANCELLED").build();
    const r = reportFired(
      { task: cancelled, plan },
      { planVersion: 1, seq: 3, firedAt: now, level: "SOUND", permissionMissing: false },
      now,
    );
    expect(r.pushes).toEqual([
      { to: "eren", hint: "task_cancelled", taskId: "task-1", event: "cancelled" },
    ]);
    expect(r.events).toEqual([]);
  });
});

describe("visibleToRecipient", () => {
  it("keeps a missed card for one day", () => {
    const task = { ...aTask().withStatus("MISSED").build(), statusChangedAt: now };
    expect(visibleToRecipient(task, MISSED_CARD_TTL_MS, addMs(now, DAY - MINUTE))).toBe(true);
    expect(visibleToRecipient(task, MISSED_CARD_TTL_MS, addMs(now, DAY))).toBe(false);
  });

  it("drops a missed card the recipient removed, but not after the task is missed again (NUD-49)", () => {
    const task = { ...aTask().withStatus("MISSED").build(), statusChangedAt: now };
    const removed = { ...task, removedByRecipientAt: addMs(now, MINUTE) };
    expect(visibleToRecipient(removed, MISSED_CARD_TTL_MS, addMs(now, 2 * MINUTE))).toBe(false);
    const missedAgain = { ...removed, statusChangedAt: addMs(now, DAY - 2 * MINUTE) };
    expect(visibleToRecipient(missedAgain, MISSED_CARD_TTL_MS, addMs(now, DAY))).toBe(true);
  });
});
