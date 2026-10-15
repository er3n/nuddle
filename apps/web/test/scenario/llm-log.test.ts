// The LLM call log (ADR 0019): every extraction and planner call is kept with its input, the
// model's answer and what came of it, linked to the task.
import { v7 as uuidv7 } from "uuid";
import { describe, expect, it } from "vitest";
import type { PokeLevel } from "../../src/domain/types";
import { ScriptedPlanner } from "../fakes";
import { at, sibelAndEren, TEXT, writeTask } from "./fixtures";

const step = (time: string, level: PokeLevel) => ({ at: at(time), level });

describe("scenarios: LLM call log", () => {
  it("a written task keeps its extraction and the adopted plan, linked to the task", async () => {
    const planner = new ScriptedPlanner(() => ({
      steps: [step("2026-10-12T07:30:00", "SOUND"), step("2026-10-12T08:00:00", "FULL_SCREEN")],
      reason: "Eren answers late in the morning.",
    }));
    const { world, sibel } = await sibelAndEren({ planner });
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    // The plan is asked for after the task's transaction, so it can be logged before the extraction.
    const calls = (await world.llmCalls()).sort((a, b) => a.kind.localeCompare(b.kind));
    expect(calls.map((c) => [c.kind, c.outcome, c.taskId, c.personId])).toEqual([
      ["extraction", "create", taskId, "sibel"],
      ["plan", "adopted", taskId, "eren"],
    ]);
    const [extraction, plan] = calls;
    expect(extraction?.input).toMatchObject({ text: TEXT.photoToEren, senderName: "Sibel" });
    expect(extraction?.writeKey).not.toBeNull();
    expect(plan).toMatchObject({ planVersion: 1, adoptedVersion: 2, model: "scripted" });
    expect(plan?.input).toMatchObject({ task: { title: "Temizlikçi için para çek" } });
    expect(plan?.output).toMatchObject({ reason: "Eren answers late in the morning." });
  });

  it("a plan that breaks the rules and a planner error are kept with why v0 stayed", async () => {
    let answer = 0;
    const planner = new ScriptedPlanner(() => {
      answer += 1;
      if (answer === 1) return { steps: [step("2026-10-12T02:00:00", "SOUND")], reason: "bad" };
      return Promise.reject(new Error("provider down"));
    });
    const { world, sibel } = await sibelAndEren({ planner });
    await writeTask(sibel, TEXT.photoToEren);
    await writeTask(sibel, TEXT.trashToEren);

    const plans = (await world.llmCalls()).filter((c) => c.kind === "plan");
    expect(plans.map((c) => [c.outcome, c.adoptedVersion])).toEqual([
      ["rejected", null],
      ["failed", null],
    ]);
    expect(plans[0]?.output).toMatchObject({ reason: "bad" });
    expect(plans[1]?.error).toBe("Error: provider down");
  });

  it("a question and a reply are kept; the task the preview creates is linked to its extraction", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    expect(await sibel.write(TEXT.hello)).toMatchObject({ kind: "chat" });
    const text = "biri bir şey yapsın";
    expect(await sibel.write(text)).toMatchObject({ kind: "ask" });

    const created = await sibel.api.POST("/api/v1/commands/create_task", {
      body: {
        idempotencyKey: uuidv7(),
        recipientId: eren.uuid,
        title: "Bir şey yap",
        detail: null,
        deadline: at("2026-10-12T19:00:00").toISOString(),
        finishMode: "done",
        rawText: text,
      },
    });
    const taskId = created.data?.taskId;
    expect(taskId).toBeDefined();

    const extractions = (await world.llmCalls()).filter((c) => c.kind === "extraction");
    expect(extractions.map((c) => [c.outcome, c.taskId])).toEqual([
      ["chat", null],
      ["ask", taskId],
    ]);
  });

  it("deleting the account removes what the LLM was told about the person", async () => {
    const { world, sibel } = await sibelAndEren();
    await writeTask(sibel, TEXT.photoToEren);
    expect((await world.llmCalls()).some((c) => c.personId === "sibel")).toBe(true);

    await sibel.deleteAccount();
    expect((await world.llmCalls()).some((c) => c.personId === "sibel")).toBe(false);
  });
});
