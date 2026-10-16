// OpenRouter's OpenAI-compatible chat completions API with structured output, shared by the task
// extractor and the poke planner (docs/architecture.md, LLM; ADR 0017).
import { z } from "zod";
import type { ExchangeSink, LlmUsage } from "../../domain/ports";

const ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

export interface OpenRouterOptions {
  apiKey: string;
  model: string;
  fetch?: typeof fetch;
  /**
   * False turns a reasoning model's thinking off. The extractor does: the sender waits for it, and
   * thinking took a write from about 1.5 s to 7-10 s (NUD-52). Default: the model decides.
   */
  reasoning?: boolean;
}

const completionSchema = z.object({
  choices: z.array(z.object({ message: z.object({ content: z.string() }) })).min(1),
  // Usage accounting (ADR 0024): `cost` is what OpenRouter charged in USD.
  usage: z
    .object({
      prompt_tokens: z.number(),
      completion_tokens: z.number(),
      cost: z.number().optional(),
    })
    .optional(),
});

function usageOf(completion: z.infer<typeof completionSchema>): LlmUsage | null {
  const u = completion.usage;
  if (!u) return null;
  return {
    promptTokens: u.prompt_tokens,
    completionTokens: u.completion_tokens,
    costUsd: u.cost ?? null,
  };
}

/** One chat completion constrained to `schema`; returns the parsed JSON content. */
export async function completeJson(
  options: OpenRouterOptions,
  request: {
    system: string;
    user: string;
    name: string;
    schema: object;
    signal?: AbortSignal;
    onExchange?: ExchangeSink;
  },
): Promise<unknown> {
  const fetchFn = options.fetch ?? fetch;
  const response = await fetchFn(ENDPOINT, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${options.apiKey}`,
      "Content-Type": "application/json",
      "X-Title": "Nuddle",
    },
    body: JSON.stringify({
      model: options.model,
      temperature: 0,
      messages: [
        { role: "system", content: request.system },
        { role: "user", content: request.user },
      ],
      response_format: {
        type: "json_schema",
        json_schema: { name: request.name, strict: true, schema: request.schema },
      },
      ...(options.reasoning === false ? { reasoning: { enabled: false } } : {}),
      usage: { include: true },
    }),
    ...(request.signal ? { signal: request.signal } : {}),
  });
  if (!response.ok) {
    throw new Error(`OpenRouter request failed with status ${String(response.status)}`);
  }
  const completion = completionSchema.parse(await response.json());
  const content = completion.choices[0]?.message.content ?? "";
  const exchange = {
    model: options.model,
    system: request.system,
    prompt: request.user,
    usage: usageOf(completion),
  };
  let parsed: unknown;
  try {
    parsed = JSON.parse(stripCodeFence(content));
  } catch (error) {
    request.onExchange?.({ ...exchange, output: content });
    throw error;
  }
  request.onExchange?.({ ...exchange, output: parsed });
  return parsed;
}

/** Some models wrap JSON in a Markdown code fence even with structured output. */
function stripCodeFence(s: string): string {
  const match = /^\s*```(?:json)?\s*([\s\S]*?)\s*```\s*$/.exec(s);
  return match?.[1] ?? s;
}
