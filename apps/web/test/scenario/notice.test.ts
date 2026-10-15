// A one-time message (NUD-79): one SOUND step at its time, "Tamam" or the time passing closes
// it as done, never missed; it cannot be snoozed or dropped, and the LLM planner is not asked.
import { describe, expect, it } from "vitest";
import { ScriptedPlanner } from "../fakes";
import { at, sibelAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: one-time messages", () => {
  it("is delivered once at its time and closes as done when the time passes", async () => {
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "never asked" }));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    const taskId = await writeTask(sibel, TEXT.loveToEren);
    const task = world.task(taskId);
    expect(task.finishMode).toBe("notice");
    expect(task.remindAt).toEqual(at("2026-10-12T15:00:00"));

    const plan = world.currentPlan(taskId);
    expect(plan?.steps.map((s) => [s.level, s.plannedAt])).toEqual([
      ["SOUND", at("2026-10-12T15:00:00")],
    ]);
    expect(planner.calls).toHaveLength(0);

    expect(await eren.command(taskId, { type: "snooze" })).toEqual({
      ok: false,
      error: "wrong_finish_mode",
    });
    expect(await eren.command(taskId, { type: "cant_today", note: null })).toEqual({
      ok: false,
      error: "wrong_finish_mode",
    });

    await world.advanceTo(at("2026-10-12T16:00:30"));
    expect(world.task(taskId).status).toBe("DONE");
    const done = world.eventsOf(taskId).filter((e) => e.type === "done");
    expect(done.map((e) => e.payload)).toEqual([{ notice: true }]);
    expect(world.eventsOf(taskId).some((e) => e.type === "missed")).toBe(false);
  });

  it("a message without a time goes out at once", async () => {
    const { world, sibel } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.dinnerToEren);
    expect(world.task(taskId).remindAt).toEqual(at("2026-10-11T20:05:00"));
    const plan = world.currentPlan(taskId) ?? { steps: [] };
    expect(plan.steps.map((s) => [s.level, s.plannedAt])).toEqual([
      ["SOUND", at("2026-10-11T20:00:00")],
    ]);
  });

  it("Tamam closes it as done at once", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.loveToEren);
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("DONE");
  });
});
