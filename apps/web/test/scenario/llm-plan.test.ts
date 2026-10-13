// The LLM poke planner end to end (ADR 0017): plan v0 first, then the checked LLM plan.
import { describe, expect, it } from "vitest";
import type { PokeLevel } from "../../src/domain/types";
import { ScriptedPlanner } from "../fakes";
import { at, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

const rings = (r: { rang: string; at: Date }[]) => r.map((x) => `${x.rang}@${hhmm(x.at)}`);
const step = (time: string, level: PokeLevel) => ({ at: at(time), level });

describe("scenarios: LLM poke plan", () => {
  it("the checked LLM plan replaces v0 and the phone rings it", async () => {
    const planner = new ScriptedPlanner(() => ({
      steps: [
        step("2026-10-12T07:30:00", "SOUND"),
        step("2026-10-12T08:00:00", "FULL_SCREEN"),
        step("2026-10-12T08:20:00", "FULL_SCREEN"),
        step("2026-10-12T08:40:00", "HUMAN"),
      ],
      reason: "Eren answers late in the morning.",
    }));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    expect(world.currentPlan(taskId)?.version).toBe(2);
    expect(
      world
        .eventsOf(taskId)
        .filter((e) => e.type === "plan_generated")
        .map((e) => e.payload.source),
    ).toEqual(["v0", "llm"]);

    await world.advanceTo(at("2026-10-12T08:50:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SOUND@07:30",
      "FULL_SCREEN@08:00",
      "FULL_SCREEN@08:20",
    ]);
    expect(world.push.to("sibel").map((p) => p.event)).toContain("human_notified");
    expect(planner.calls[0]).toMatchObject({
      sender: { name: "Sibel", self: false },
      recipient: { name: "Eren" },
      relationship: { recipientIsToSender: "spouse", senderIsToRecipient: "spouse" },
      humanAllowed: true,
      task: { title: "Temizlikçi için para çek", timing: "by", floor: "SILENT" },
    });
    expect(world.violations).toEqual([]);
  });

  it("an answer that breaks the rules keeps v0", async () => {
    // Inside quiet hours and after the deadline: nothing valid is left.
    const planner = new ScriptedPlanner(() => ({
      steps: [step("2026-10-12T02:00:00", "SOUND"), step("2026-10-12T09:30:00", "FULL_SCREEN")],
      reason: "bad",
    }));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    expect(world.currentPlan(taskId)?.version).toBe(1);
    await world.advanceTo(at("2026-10-12T08:50:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@08:00",
      "SOUND@08:30",
      "FULL_SCREEN@08:45",
    ]);
  });

  it("a planner error keeps v0 and the task still works", async () => {
    const planner = new ScriptedPlanner(() => Promise.reject(new Error("provider down")));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    expect(world.currentPlan(taskId)?.version).toBe(1);
    await world.advanceTo(at("2026-10-12T08:50:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toHaveLength(3);
  });

  it("the planner reads the recipient's history: who wrote it, which poke rang, when they acted", async () => {
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "keep v0" }));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    const first = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:40:00"));
    expect(await eren.command(first, { type: "done" })).toEqual({ ok: true });

    await writeTask(sibel, TEXT.dishesToEren);
    const asked = planner.calls.at(-1);
    expect(asked?.history).toHaveLength(1);
    expect(asked?.history[0]).toMatchObject({
      title: "Çöpü at",
      from: "Sibel",
      relationship: "spouse",
      outcome: "DONE",
      actedAt: at("2026-10-12T18:40:00"),
    });
    expect(asked?.history[0]?.pokes.map((p) => p.level)).toEqual(["SILENT", "SOUND"]);
    expect(world.violations).toEqual([]);
  });
});
