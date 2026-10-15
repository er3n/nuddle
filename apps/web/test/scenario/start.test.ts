// The recipient moves when the pokes begin (NUD-78): the deadline stays, the plan starts later.
import { describe, expect, it } from "vitest";
import { ScriptedPlanner } from "../fakes";
import { at, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: the recipient's start", () => {
  it("a by task: the plan window and the planner's start move to the chosen time", async () => {
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "keep v0" }));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    // "eren yarın akşam çöpü atsın": Monday 19:00; v0 starts an hour before.
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect(
      hhmm((world.currentPlan(taskId) ?? { steps: [] }).steps[0]?.plannedAt ?? new Date(0)),
    ).toBe("18:00");

    const versionBefore = world.task(taskId).planVersion;
    expect(
      await eren.command(taskId, { type: "set_start", startAt: at("2026-10-12T18:40:00") }),
    ).toEqual({ ok: true });
    const task = world.task(taskId);
    expect(task.pokeFrom).toEqual(at("2026-10-12T18:40:00"));
    expect(task.deadline).toEqual(at("2026-10-12T19:00:00"));
    expect(task.planVersion).toBeGreaterThan(versionBefore);
    const plan = world.currentPlan(taskId) ?? { steps: [] };
    expect(plan.steps.length).toBeGreaterThan(0);
    for (const step of plan.steps)
      expect(step.plannedAt.getTime()).toBeGreaterThanOrEqual(at("2026-10-12T18:40:00").getTime());
    expect(planner.calls.at(-1)?.task.startAt).toEqual(at("2026-10-12T18:40:00"));
    expect(world.eventsOf(taskId).map((e) => e.type)).toContain("start_set");
  });

  it("an at task: the start becomes the lead, within its limit", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    // 17:30 pickup with a 20 minute walk: the pokes start at 17:10.
    const taskId = await writeTask(sibel, TEXT.pickupKidsAt);
    expect(world.task(taskId).leadMinutes).toBe(20);
    expect(
      await eren.command(taskId, { type: "set_start", startAt: at("2026-10-12T16:45:00") }),
    ).toEqual({ ok: true });
    expect(world.task(taskId).leadMinutes).toBe(45);
    expect(world.task(taskId).remindAt).toEqual(at("2026-10-12T17:30:00"));
    expect(
      hhmm((world.currentPlan(taskId) ?? { steps: [] }).steps[0]?.plannedAt ?? new Date(0)),
    ).toBe("16:45");

    // After the moment, or more than six hours before it: refused.
    expect(
      await eren.command(taskId, { type: "set_start", startAt: at("2026-10-12T17:35:00") }),
    ).toEqual({
      ok: false,
      error: "start_not_in_window",
    });
    expect(
      await eren.command(taskId, { type: "set_start", startAt: at("2026-10-12T08:00:00") }),
    ).toEqual({
      ok: false,
      error: "start_not_in_window",
    });
  });

  it("only the recipient, only while the task is scheduled, never in the past", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect(
      await sibel.command(taskId, { type: "set_start", startAt: at("2026-10-12T18:40:00") }),
    ).toEqual({
      ok: false,
      error: "not_recipient",
    });
    expect(
      await eren.command(taskId, { type: "set_start", startAt: at("2026-10-11T19:00:00") }),
    ).toEqual({
      ok: false,
      error: "start_not_in_window",
    });
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });
    expect(
      await eren.command(taskId, { type: "set_start", startAt: at("2026-10-12T18:40:00") }),
    ).toEqual({
      ok: false,
      error: "wrong_status",
    });
    expect(world.task(taskId).status).toBe("DONE");
  });
});
