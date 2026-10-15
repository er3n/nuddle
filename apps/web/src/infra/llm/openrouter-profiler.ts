// PersonProfiler over OpenRouter (ADR 0020): a short text on how the person responds to pokes,
// written from the previous profile and the recent closed tasks. Read by the planner, never by
// people.
import { z } from "zod";
import type { HistoryItem } from "../../domain/planner";
import { PROFILE_MAX_WORDS, type ProfileInput } from "../../domain/profile";
import type { ExchangeSink, PersonProfiler } from "../../domain/ports";
import { toLocalIso } from "../../domain/time";
import { completeJson, type OpenRouterOptions } from "./openrouter";

const answerSchema = z.object({ profile: z.string().min(1) });

const jsonSchema = {
  type: "object",
  additionalProperties: false,
  required: ["profile"],
  properties: { profile: { type: "string" } },
};

const RULES = `You keep a short profile of one person in a family reminder app: how they respond to pokes (alarms on their phone that escalate until a task is done). The planner reads it to choose the pokes for the next task. Nobody else reads it.

Write at most ${String(PROFILE_MAX_WORDS)} words of plain English, as facts with numbers where you have them. Cover, when the history shows it:
- which poke level usually gets them to act (SILENT, SOUND, FULL_SCREEN) and how long after it;
- whether they act before any poke, snooze, say can't today, or let tasks be missed;
- differences by who wrote the task, by time of day, by kind of task;
- anything else a planner should know (a sender they answer at once, mornings they sleep through).
Start from the previous profile and update it with the new tasks: keep what still holds, drop what the new tasks contradict, do not repeat the task list. With too little history, say so in one line.`;

/** The profiler input as JSON, with every time in the recipient's time zone. */
export function buildProfilePrompt(input: ProfileInput): string {
  const tz = input.timeZone;
  const at = (t: Date) => toLocalIso(t, tz);
  const item = (h: HistoryItem) => ({
    ...h,
    time: at(h.time),
    deadline: at(h.deadline),
    actedAt: h.actedAt ? at(h.actedAt) : null,
    pokes: h.pokes.map((p) => ({
      level: p.level,
      plannedAt: at(p.plannedAt),
      firedAt: at(p.firedAt),
    })),
  });
  return JSON.stringify(
    {
      now: at(input.now),
      timeZone: tz,
      recipient: input.recipient,
      previousProfile: input.previous,
      history: input.history.map(item),
    },
    null,
    1,
  );
}

export class OpenRouterProfiler implements PersonProfiler {
  constructor(private readonly options: OpenRouterOptions & { timeoutMs?: number }) {}

  async profile(input: ProfileInput, onExchange?: ExchangeSink): Promise<string> {
    const json = await completeJson(this.options, {
      ...(onExchange ? { onExchange } : {}),
      system: RULES,
      user: buildProfilePrompt(input),
      name: "poke_profile",
      schema: jsonSchema,
      signal: AbortSignal.timeout(this.options.timeoutMs ?? 30_000),
    });
    return answerSchema.parse(json).profile.trim();
  }
}
