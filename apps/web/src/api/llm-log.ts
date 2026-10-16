// The LLM call log (ADR 0019): every extraction and planner call is kept with what the model got,
// what it said and what came of it. Writing the log never breaks the request it describes.
import { randomUUID } from "node:crypto";
import {
  capReachedUntil,
  capWindowStart,
  LLM_DAILY_CAPS,
  type LlmCallKind,
} from "../domain/cost-caps";
import type { ExchangeSink, LlmExchange } from "../domain/ports";
import type { PersonId } from "../domain/types";
import { insertLlmCall, llmCallWindow, type LlmCallRow } from "../infra/db/store";
import type { AppDeps } from "./deps";

/** A call in flight: the sink to hand to the port and what it told, with the time it took. */
export class LlmCall {
  private exchange: LlmExchange | null = null;
  private readonly started = performance.now();

  readonly sink: ExchangeSink = (x) => {
    this.exchange = x;
  };

  constructor(
    private readonly deps: AppDeps,
    private readonly base: Pick<LlmCallRow, "kind" | "personId" | "input"> &
      Partial<Pick<LlmCallRow, "taskId" | "writeKey" | "planVersion">>,
  ) {}

  /** Store the call once its outcome is known. */
  async record(
    outcome: string,
    more: Partial<Pick<LlmCallRow, "taskId" | "adoptedVersion" | "error">> = {},
  ): Promise<void> {
    const x = this.exchange;
    try {
      await insertLlmCall(this.deps.db, {
        id: randomUUID(),
        taskId: null,
        writeKey: null,
        planVersion: null,
        adoptedVersion: null,
        error: null,
        ...this.base,
        ...more,
        model: x?.model ?? null,
        system: x?.system ?? null,
        prompt: x?.prompt ?? null,
        output: x?.output ?? null,
        outcome,
        latencyMs: Math.round(performance.now() - this.started),
        promptTokens: x?.usage?.promptTokens ?? null,
        completionTokens: x?.usage?.completionTokens ?? null,
        costUsd: x?.usage?.costUsd ?? null,
        // The daily caps count rows by this time, on the same clock as their window (ADR 0009).
        createdAt: this.deps.clock.now(),
      });
    } catch (error) {
      console.error("llm call log failed", this.base.kind, outcome, error);
    }
  }
}

/**
 * When the person's calls of this kind in the last 24 hours reached the cap (ADR 0024): the time
 * the next one is allowed; null while under it.
 */
export async function llmCapReachedUntil(
  deps: AppDeps,
  personId: PersonId,
  kind: LlmCallKind,
): Promise<Date | null> {
  const now = deps.clock.now();
  const window = await llmCallWindow(deps.db, personId, kind, capWindowStart(now));
  return capReachedUntil(window, LLM_DAILY_CAPS[kind], now);
}

/** The error as one line for the log. */
export function errorText(error: unknown): string {
  if (error instanceof Error) return `${error.name}: ${error.message}`;
  return String(error);
}
