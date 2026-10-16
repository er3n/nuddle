// Cost guardrails end to end (ADR 0024): daily caps on LLM calls per person, and every call's
// tokens and cost in the LLM call log.
import { v7 as uuidv7 } from "uuid";
import { describe, expect, it } from "vitest";
import { LLM_DAILY_CAPS } from "../../src/domain/cost-caps";
import type { PokePlanner } from "../../src/domain/ports";
import { HOUR } from "../../src/domain/time";
import { ScriptedPlanner } from "../fakes";
import { sibelAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: cost guardrails", () => {
  it("a writer over the daily extraction cap is refused until the oldest call is a day old", async () => {
    const { world, sibel } = await sibelAndEren();
    const oldest = new Date(world.clock.now().getTime() - 23 * HOUR);
    await world.fillLlmCalls(sibel, "extraction", 1, oldest);
    await world.fillLlmCalls(sibel, "extraction", LLM_DAILY_CAPS.extraction - 1);
    const extractions = world.extractor.calls.length;

    const refused = await sibel.api.POST("/api/v1/commands/write_task", {
      body: { idempotencyKey: uuidv7(), text: TEXT.photoToEren },
    });
    expect(refused.response.status).toBe(429);
    expect(refused.error?.error).toMatchObject({
      code: "DRT-1010",
      name: "write_limit",
      details: { retryAt: new Date(oldest.getTime() + 24 * HOUR).toISOString() },
    });
    // Nothing reached the model, and nothing was logged for it.
    expect(world.extractor.calls).toHaveLength(extractions);
    expect((await world.llmCalls()).filter((c) => c.outcome !== "filled")).toEqual([]);

    // An hour later the oldest call has left the window.
    await world.advanceBy(HOUR);
    expect(await sibel.write(TEXT.photoToEren)).toMatchObject({ kind: "created" });
  });

  it("another person's calls do not count toward the writer's cap", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    await world.fillLlmCalls(eren, "extraction", LLM_DAILY_CAPS.extraction);
    expect(await sibel.write(TEXT.photoToEren)).toMatchObject({ kind: "created" });
  });

  it("a recipient over the daily plan cap keeps v0, and the log says why", async () => {
    const planner = new ScriptedPlanner(() => ({ steps: [], reason: "never asked" }));
    const { world, sibel, eren } = await sibelAndEren({ planner });
    await world.fillLlmCalls(eren, "plan", LLM_DAILY_CAPS.plan);
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    expect(planner.calls).toEqual([]);
    expect(world.currentPlan(taskId)?.version).toBe(1);
    const plans = (await world.llmCalls()).filter((c) => c.kind === "plan" && c.taskId === taskId);
    expect(plans.map((c) => [c.outcome, c.model])).toEqual([["capped", null]]);
    expect(plans[0]?.input).toMatchObject({ task: { title: "Temizlikçi için para çek" } });
  });

  it("each call keeps the tokens and the cost the provider reported", async () => {
    const planner: PokePlanner = {
      plan(_input, onExchange) {
        const proposal = { steps: [], reason: "keep v0" };
        onExchange?.({
          model: "metered",
          system: "",
          prompt: "",
          output: proposal,
          usage: { promptTokens: 1830, completionTokens: 95, costUsd: 0.00021 },
        });
        return Promise.resolve(proposal);
      },
    };
    const { world, sibel } = await sibelAndEren({ planner });
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    const calls = await world.llmCalls();
    expect(
      calls
        .filter((c) => c.taskId === taskId)
        .map((c) => [c.kind, c.promptTokens, c.completionTokens, c.costUsd])
        .sort(),
    ).toEqual([
      // The fixture extractor has no model, so nothing was used.
      ["extraction", null, null, null],
      ["plan", 1830, 95, 0.00021],
    ]);
  });

  it("deleting the account removes the codes sent to the number", async () => {
    const { world, eren } = await sibelAndEren();
    await world.fillSmsSends(eren, 2);
    expect(await world.rowsHolding(eren)).toMatchObject({ smsSends: 2 });

    await eren.deleteAccount();
    expect(await world.rowsHolding(eren)).not.toHaveProperty("smsSends");
  });
});
