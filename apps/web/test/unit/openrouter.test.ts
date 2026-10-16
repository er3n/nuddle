// The shared OpenRouter call (ADR 0017): usage accounting is asked for, and the tokens and cost
// the answer carries reach the LLM call log (ADR 0024).
import { describe, expect, it } from "vitest";
import type { LlmExchange } from "../../src/domain/ports";
import { completeJson } from "../../src/infra/llm/openrouter";

function answering(body: object, bodies: string[] = []): typeof fetch {
  return (_url, init) => {
    bodies.push(init?.body as string);
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
  };
}

const request = (onExchange: (x: LlmExchange) => void) => ({
  system: "s",
  user: "u",
  name: "answer",
  schema: {},
  onExchange,
});

describe("openrouter", () => {
  it("asks for usage and reports tokens and cost with the exchange", async () => {
    const bodies: string[] = [];
    const fetchFn = answering(
      {
        choices: [{ message: { content: '{"ok":true}' } }],
        usage: { prompt_tokens: 1200, completion_tokens: 80, total_tokens: 1280, cost: 0.000143 },
      },
      bodies,
    );
    const seen: LlmExchange[] = [];
    const out = await completeJson(
      { apiKey: "k", model: "m/x", fetch: fetchFn },
      request((x) => seen.push(x)),
    );

    expect(out).toEqual({ ok: true });
    expect(JSON.parse(bodies[0] ?? "{}")).toMatchObject({ usage: { include: true } });
    expect(seen[0]?.usage).toEqual({ promptTokens: 1200, completionTokens: 80, costUsd: 0.000143 });
  });

  it("keeps the tokens without a cost, and nothing when the answer has no usage", async () => {
    const seen: LlmExchange[] = [];
    await completeJson(
      {
        apiKey: "k",
        model: "m/x",
        fetch: answering({
          choices: [{ message: { content: "{}" } }],
          usage: { prompt_tokens: 10, completion_tokens: 2 },
        }),
      },
      request((x) => seen.push(x)),
    );
    await completeJson(
      {
        apiKey: "k",
        model: "m/x",
        fetch: answering({ choices: [{ message: { content: "{}" } }] }),
      },
      request((x) => seen.push(x)),
    );
    expect(seen.map((x) => x.usage)).toEqual([
      { promptTokens: 10, completionTokens: 2, costUsd: null },
      null,
    ]);
  });

  it("an answer that is not JSON still reports what it used", async () => {
    const seen: LlmExchange[] = [];
    await expect(
      completeJson(
        {
          apiKey: "k",
          model: "m/x",
          fetch: answering({
            choices: [{ message: { content: "not json" } }],
            usage: { prompt_tokens: 5, completion_tokens: 3, cost: 0.00001 },
          }),
        },
        request((x) => seen.push(x)),
      ),
    ).rejects.toThrow();
    expect(seen[0]).toMatchObject({ output: "not json", usage: { promptTokens: 5 } });
  });
});
