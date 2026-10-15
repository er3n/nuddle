// PokePlanner over OpenRouter (ADR 0017). The model proposes the steps; `checkPlan` enforces the
// rules afterwards, so the prompt states them only to waste fewer steps.
import { z } from "zod";
import type { PlannerInput, PlanProposal } from "../../domain/planner";
import type { ExchangeSink, PokePlanner } from "../../domain/ports";
import { toLocalIso } from "../../domain/time";
import { completeJson, type OpenRouterOptions } from "./openrouter";

const levels = ["SILENT", "SOUND", "FULL_SCREEN", "HUMAN"] as const;

const proposalSchema = z.object({
  steps: z.array(z.object({ at: z.string(), level: z.enum(levels) })),
  reason: z.string(),
});

const jsonSchema = {
  type: "object",
  additionalProperties: false,
  required: ["steps", "reason"],
  properties: {
    steps: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["at", "level"],
        properties: {
          at: { type: "string" },
          level: { type: "string", enum: levels },
        },
      },
    },
    reason: { type: "string" },
  },
};

const RULES = `You plan the pokes for one task in a family reminder app. A poke is an alarm on the recipient's phone that keeps coming until the task is done. Nobody sees the plan.

Levels
- SILENT: a quiet notification.
- SOUND: a notification with sound.
- FULL_SCREEN: the screen turns on over the lock screen with a loud alarm until answered.
- HUMAN: the app tells the sender that the recipient does not answer, so they can call. Only if humanAllowed.

Goal
- The task gets done in time, with as few and as gentle pokes as work for this person.
- Read the history (actedAfter says after which poke that rang, at which level, and how many seconds later the person acted): which poke usually got this person to act, how long after it they acted, whether they snooze, how they answer this sender, the time of day, similar tasks. Someone who always acts on the first SOUND needs no FULL_SCREEN early; someone who ignores two pokes needs FULL_SCREEN sooner.
- Think about how long the task takes: drinking water takes a minute, going to the bank takes an hour. For a "by" task the last device poke must leave enough time to do it.
- Do not spend steps on levels that never got this person to act in the history: if a SILENT poke never did, do not start with SILENT.
- For an "at" task the first poke comes at the moment itself (not earlier) and is at least SOUND, since a quiet notification at the moment is easy to miss; then it escalates if nothing happens.
- With no history: a short ladder that reaches FULL_SCREEN well before the deadline.

Hard rules (a step that breaks one is dropped)
- "at" is ISO 8601 with the UTC offset of the given time zone.
- Not before task.notBefore. For an "at" task not before task.time. Before task.deadline.
- Never inside quiet hours.
- Levels never go down; nothing below task.floor.
- At most 8 device steps, at least 3 minutes apart. At most one HUMAN step, and it is the last step.
- SILENT alone is not a plan.

reason: one short English sentence on why this plan fits this person.`;

const at = (t: Date, timeZone: string): string => toLocalIso(t, timeZone);

/** The planner input as JSON, with every time in the recipient's time zone. */
export function buildPlannerPrompt(input: PlannerInput): string {
  const tz = input.timeZone;
  return JSON.stringify(
    {
      now: at(input.now, tz),
      weekday: new Intl.DateTimeFormat("en-US", { timeZone: tz, weekday: "long" }).format(
        input.now,
      ),
      timeZone: tz,
      task: {
        ...input.task,
        time: at(input.task.time, tz),
        deadline: at(input.task.deadline, tz),
        notBefore: at(input.task.notBefore, tz),
      },
      sender: input.sender,
      recipient: input.recipient,
      relationship: input.relationship,
      recipientConnections: input.recipientConnections,
      quietHours: input.quietHours,
      reachability: input.reachability,
      humanAllowed: input.humanAllowed,
      history: input.history.map((h) => ({
        ...h,
        time: at(h.time, tz),
        deadline: at(h.deadline, tz),
        actedAt: h.actedAt ? at(h.actedAt, tz) : null,
        pokes: h.pokes.map((p) => ({
          level: p.level,
          plannedAt: at(p.plannedAt, tz),
          firedAt: at(p.firedAt, tz),
        })),
      })),
    },
    null,
    1,
  );
}

export class OpenRouterPlanner implements PokePlanner {
  constructor(private readonly options: OpenRouterOptions & { timeoutMs?: number }) {}

  async plan(input: PlannerInput, onExchange?: ExchangeSink): Promise<PlanProposal> {
    const json = await completeJson(this.options, {
      ...(onExchange ? { onExchange } : {}),
      system: RULES,
      user: buildPlannerPrompt(input),
      name: "poke_plan",
      schema: jsonSchema,
      signal: AbortSignal.timeout(this.options.timeoutMs ?? 10_000),
    });
    const parsed = proposalSchema.parse(json);
    return {
      steps: parsed.steps.map((s) => ({ at: new Date(s.at), level: s.level })),
      reason: parsed.reason,
    };
  }
}
