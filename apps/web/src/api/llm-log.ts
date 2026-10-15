// The LLM call log (ADR 0019): every extraction and planner call is kept with what the model got,
// what it said and what came of it. Writing the log never breaks the request it describes.
import { randomUUID } from "node:crypto";
import type { ExchangeSink, LlmExchange } from "../domain/ports";
import { insertLlmCall, type LlmCallRow } from "../infra/db/store";
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
      });
    } catch (error) {
      console.error("llm call log failed", this.base.kind, outcome, error);
    }
  }
}

/** The error as one line for the log. */
export function errorText(error: unknown): string {
  if (error instanceof Error) return `${error.name}: ${error.message}`;
  return String(error);
}
