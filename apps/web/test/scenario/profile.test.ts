// The person profile (ADR 0020): written in the background once enough tasks closed, read by the
// planner next to the last 8 tasks, logged, and gone with the account.
import { describe, expect, it } from "vitest";
import { RECENT_TASKS } from "../../src/domain/profile";
import { ScriptedPlanner, ScriptedProfiler } from "../fakes";
import type { Client } from "./harness";
import { sibelAndEren, TEXT, writeTask } from "./fixtures";

/** Sibel writes a task to Eren and he does it at once: one more closed task. */
async function closeOne(sibel: Client, eren: Client): Promise<void> {
  const taskId = await writeTask(sibel, TEXT.trashToEren);
  expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });
}

describe("scenarios: person profile", () => {
  it("is written after the 10th closed task, and the planner then gets it with the last 8 tasks", async () => {
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "keep v0" }));
    const profiler = new ScriptedProfiler(
      (input) =>
        `${input.recipient.name} acts on the first SOUND; ${String(input.history.length)} tasks seen.`,
    );
    const { world, sibel, eren } = await sibelAndEren({ planner, profiler });

    for (let i = 0; i < 9; i++) await closeOne(sibel, eren);
    expect(profiler.calls).toHaveLength(0);
    expect(planner.calls.at(-1)?.profile).toBeNull();
    expect(planner.calls.at(-1)?.history).toHaveLength(8);

    await closeOne(sibel, eren);
    // The 11th task is the first planned with 10 closed: it is planned with no profile, and the
    // profile is written right after it, for the next plan (never before, a slow profiler must not
    // delay the pokes).
    await writeTask(sibel, TEXT.dishesToEren);
    expect(planner.calls.at(-1)?.profile).toBeNull();
    expect(profiler.calls).toHaveLength(1);
    expect(profiler.calls[0]).toMatchObject({ previous: null, recipient: { name: "Eren" } });
    expect(profiler.calls[0]?.history).toHaveLength(10);
    expect(world.person("eren").pokeProfile).toMatchObject({ tasksSeen: 10 });

    // The next plan reads it; not written again until 10 more closed; it is in the call log.
    await writeTask(sibel, TEXT.photoToEren);
    expect(planner.calls.at(-1)?.profile).toBe("Eren acts on the first SOUND; 10 tasks seen.");
    expect(planner.calls.at(-1)?.history).toHaveLength(RECENT_TASKS);
    expect(profiler.calls).toHaveLength(1);
    const logged = (await world.llmCalls()).filter((c) => c.kind === "profile");
    expect(logged.map((c) => [c.outcome, c.personId])).toEqual([["written", "eren"]]);
  });

  it("a failed profiler keeps the old profile and the plan still happens", async () => {
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "keep v0" }));
    let fail = false;
    const profiler = new ScriptedProfiler(() =>
      fail ? Promise.reject(new Error("down")) : "First profile.",
    );
    const { world, sibel, eren } = await sibelAndEren({ planner, profiler });
    for (let i = 0; i < 10; i++) await closeOne(sibel, eren);
    await writeTask(sibel, TEXT.dishesToEren);
    expect(world.person("eren").pokeProfile?.text).toBe("First profile.");

    fail = true;
    for (let i = 0; i < 10; i++) await closeOne(sibel, eren);
    const before = planner.calls.length;
    await writeTask(sibel, TEXT.dishesToEren);
    expect(planner.calls).toHaveLength(before + 1);
    expect(planner.calls.at(-1)?.profile).toBe("First profile.");
    expect(world.person("eren").pokeProfile?.text).toBe("First profile.");
    const logged = (await world.llmCalls()).filter((c) => c.kind === "profile");
    expect(logged.map((c) => c.outcome)).toEqual(["written", "failed"]);
  });

  it("goes with the account", async () => {
    const profiler = new ScriptedProfiler(() => "A profile.");
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "keep v0" }));
    const { world, sibel, eren } = await sibelAndEren({ planner, profiler });
    for (let i = 0; i < 10; i++) await closeOne(sibel, eren);
    await writeTask(sibel, TEXT.dishesToEren);
    expect(world.person("eren").pokeProfile).not.toBeNull();
    await eren.deleteAccount();
    expect(world.person("eren").pokeProfile).toBeNull();
  });
});
