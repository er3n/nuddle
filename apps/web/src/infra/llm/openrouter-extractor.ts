// TaskExtractor over OpenRouter's OpenAI-compatible chat completions API with structured output.
// Provider and model: docs/architecture.md (LLM), open-questions#16. Env: OPENROUTER_API_KEY,
// OPENROUTER_MODEL.
import { z } from "zod";
import type { Extraction, ExtractionInput } from "../../domain/extraction";
import type { TaskExtractor } from "../../domain/ports";
import { toLocalIso } from "../../domain/time";

const ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

const extractionSchema = z.object({
  recipient: z.string().nullable(),
  title: z.string().nullable(),
  detail: z.string().nullable(),
  deadline: z.string().nullable(),
  finishMode: z.enum(["done", "photo"]),
  missing: z.array(z.enum(["recipient", "title", "deadline"])),
});

const nullableString = { type: ["string", "null"] };

const jsonSchema = {
  type: "object",
  additionalProperties: false,
  required: ["recipient", "title", "detail", "deadline", "finishMode", "missing"],
  properties: {
    recipient: nullableString,
    title: nullableString,
    detail: nullableString,
    deadline: nullableString,
    finishMode: { type: "string", enum: ["done", "photo"] },
    missing: { type: "array", items: { type: "string", enum: ["recipient", "title", "deadline"] } },
  },
};

const completionSchema = z.object({
  choices: z.array(z.object({ message: z.object({ content: z.string() }) })).min(1),
});

const RULES = `You turn one sentence (usually Turkish) into a reminder task for a family reminder app.
Return only JSON that matches the schema.

recipient
- "self" when the sender writes to themselves: first person ("ödeyeyim"), "bana", "beni", or a plain imperative with no person named.
- Otherwise exactly one name from the connection list, spelled as in the list. A relationship word ("annem", "eşim", "babam") means the single connection with that relationship.
- The recipient is the person who must do the task (the subject of the verb). A person who is only the object ("annemi ara", "Eren'e yaz") is not the recipient.
- If the person is not in the list, or more than one connection could be meant ("çocuklar", "biri"), use null and add "recipient" to missing.

title
- A short Turkish imperative, first letter capitalized, without the person, amount or time. Example: "Temizlikçi için para çek".

detail
- Amounts and specifics not in the title, Turkish formatting: "1.000 TL", "250 TL". Numbers in words become digits ("bin lira" is "1.000 TL"). Otherwise null.

deadline
- ISO 8601 in the sender's time zone with its UTC offset, seconds 00, e.g. "2026-10-12T09:00:00+03:00".
- Parts of the day: sabah 09:00, öğlen 12:00, öğleden sonra 15:00, akşam 19:00, gece 22:00.
- "9dan önce", "10a kadar": that hour. An hour 1-6 with no part of day is afternoon (13:00-18:00); 7-12 is as written.
- A day with no time means 19:00 that day. A time with no day means today if still ahead, otherwise tomorrow.
- Relative times ("2 saat içinde", "20 dakika sonra") count from the sender's local time.
- A weekday means its next occurrence after today.
- No time and no day at all: null and add "deadline" to missing.

finishMode
- "photo" for money: withdrawing, sending, paying bills, rent, dues, taxes, or any task with a money amount. Otherwise "done".

missing
- The fields you could not determine. Empty when the task is complete.`;

function weekday(t: Date, timeZone: string): string {
  return new Intl.DateTimeFormat("en-US", { timeZone, weekday: "long" }).format(t);
}

export function buildPrompt(input: ExtractionInput): string {
  const connections =
    input.connections.length === 0
      ? "(none)"
      : input.connections.map((c) => `- ${c.name}: ${c.relationship}`).join("\n");
  return [
    `Sender: ${input.senderName}`,
    `Sender's local time: ${toLocalIso(input.now, input.timeZone)} (${weekday(input.now, input.timeZone)}), time zone ${input.timeZone}`,
    `Sender's connections (name: relationship, the relationship is what the person is to the sender):`,
    connections,
    ``,
    `Text: ${input.text}`,
  ].join("\n");
}

export interface OpenRouterOptions {
  apiKey: string;
  model: string;
  fetch?: typeof fetch;
}

export class OpenRouterExtractor implements TaskExtractor {
  private readonly fetchFn: typeof fetch;

  constructor(private readonly options: OpenRouterOptions) {
    this.fetchFn = options.fetch ?? fetch;
  }

  async extract(input: ExtractionInput): Promise<Extraction> {
    const response = await this.fetchFn(ENDPOINT, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.options.apiKey}`,
        "Content-Type": "application/json",
        "X-Title": "Nuddle",
      },
      body: JSON.stringify({
        model: this.options.model,
        temperature: 0,
        messages: [
          { role: "system", content: RULES },
          { role: "user", content: buildPrompt(input) },
        ],
        response_format: {
          type: "json_schema",
          json_schema: { name: "task_extraction", strict: true, schema: jsonSchema },
        },
      }),
    });
    if (!response.ok) {
      throw new Error(`OpenRouter request failed with status ${String(response.status)}`);
    }
    const completion = completionSchema.parse(await response.json());
    const content = completion.choices[0]?.message.content ?? "";
    return extractionSchema.parse(JSON.parse(stripCodeFence(content)));
  }
}

/** Some models wrap JSON in a Markdown code fence even with structured output. */
function stripCodeFence(s: string): string {
  const match = /^\s*```(?:json)?\s*([\s\S]*?)\s*```\s*$/.exec(s);
  return match?.[1] ?? s;
}
