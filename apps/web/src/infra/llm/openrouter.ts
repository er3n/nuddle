// OpenRouter's OpenAI-compatible chat completions API with structured output, shared by the task
// extractor and the poke planner (docs/architecture.md, LLM; ADR 0017).
import { z } from "zod";

const ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

export interface OpenRouterOptions {
  apiKey: string;
  model: string;
  fetch?: typeof fetch;
}

const completionSchema = z.object({
  choices: z.array(z.object({ message: z.object({ content: z.string() }) })).min(1),
});

/** One chat completion constrained to `schema`; returns the parsed JSON content. */
export async function completeJson(
  options: OpenRouterOptions,
  request: { system: string; user: string; name: string; schema: object; signal?: AbortSignal },
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
    }),
    ...(request.signal ? { signal: request.signal } : {}),
  });
  if (!response.ok) {
    throw new Error(`OpenRouter request failed with status ${String(response.status)}`);
  }
  const completion = completionSchema.parse(await response.json());
  const content = completion.choices[0]?.message.content ?? "";
  return JSON.parse(stripCodeFence(content));
}

/** Some models wrap JSON in a Markdown code fence even with structured output. */
function stripCodeFence(s: string): string {
  const match = /^\s*```(?:json)?\s*([\s\S]*?)\s*```\s*$/.exec(s);
  return match?.[1] ?? s;
}
