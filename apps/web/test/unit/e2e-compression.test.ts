// E2E mode compresses poke plans so steps are seconds apart (docs/testing.md, Backend E2E mode).
import { describe, expect, it } from "vitest";
import { compressOutcome } from "../../src/api/e2e";
import { createTask } from "../../src/domain/task-machine";
import { aPerson } from "../builders";

describe("compressOutcome", () => {
  it("moves steps and their timers toward now, 60 times closer, and keeps the deadline", () => {
    const now = new Date("2026-10-12T17:00:00+03:00");
    const selin = aPerson("Selin");
    const eren = aPerson("Eren");
    const r = createTask({
      id: "task-1",
      sender: selin,
      recipient: eren,
      edge: { fromPersonId: "selin", toPersonId: "eren", type: "spouse", status: "active" },
      title: "Çöpü at",
      detail: null,
      deadline: new Date("2026-10-12T19:00:00+03:00"),
      finishMode: "done",
      rawText: null,
      reachability: "FULL_SCREEN",
      now,
    });
    if (!r.ok) throw new Error(r.error);
    const compressed = compressOutcome(r.value, now);

    expect(compressed.plan.steps.length).toBe(r.value.plan.steps.length);
    r.value.plan.steps.forEach((s, i) => {
      const offset = s.plannedAt.getTime() - now.getTime();
      expect(compressed.plan.steps[i]?.plannedAt.getTime()).toBe(now.getTime() + offset / 60);
    });
    // The last step lands within two minutes, not two hours.
    expect(
      Math.max(...compressed.plan.steps.map((s) => s.plannedAt.getTime())) - now.getTime(),
    ).toBeLessThan(2 * 60_000);
    expect(compressed.task.deadline).toEqual(r.value.task.deadline);
    expect(compressed.scheduledActions.find((a) => a.kind === "deadline")?.dueAt).toEqual(
      r.value.task.deadline,
    );
  });
});
