// One E2E run: its folder under e2e-runs/, the log, and the steps with their timings and screenshots
// (docs/testing.md, Orchestrator). Agents debug from that folder.
import { appendFileSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export const repoRoot = fileURLToPath(new URL("../../../", import.meta.url));

export interface StepRecord {
  name: string;
  status: "passed" | "failed";
  startedAt: string;
  seconds: number;
  error?: string;
}

export class RunContext {
  readonly dir: string;
  readonly steps: StepRecord[] = [];
  private stepCount = 0;
  /** Called after every step, passed or failed; the orchestrator screenshots both devices here. */
  afterStep: (stepDir: string) => Promise<void> = () => Promise.resolve();

  constructor(now: Date) {
    const stamp = now
      .toISOString()
      .replace(/\.\d+Z$/, "Z")
      .replaceAll(":", "-");
    this.dir = join(repoRoot, "e2e-runs", stamp);
    mkdirSync(this.dir, { recursive: true });
  }

  path(...parts: string[]): string {
    return join(this.dir, ...parts);
  }

  log(message: string): void {
    const line = `${new Date().toISOString()} ${message}`;
    console.log(line);
    appendFileSync(this.path("orchestrator.log"), `${line}\n`);
  }

  writeJson(name: string, value: unknown): void {
    writeFileSync(this.path(name), `${JSON.stringify(value, null, 2)}\n`);
  }

  /** Runs one named step; its folder `steps/NN-name/` gets the screenshots and UI dumps. */
  async step<T>(name: string, fn: (stepDir: string) => Promise<T>): Promise<T> {
    this.stepCount += 1;
    const stepDir = this.path("steps", `${String(this.stepCount).padStart(2, "0")}-${name}`);
    mkdirSync(stepDir, { recursive: true });
    const started = Date.now();
    this.log(`step ${name}: start`);
    const finish = (status: StepRecord["status"], error?: string): void => {
      const seconds = Math.round((Date.now() - started) / 100) / 10;
      this.steps.push({
        name,
        status,
        startedAt: new Date(started).toISOString(),
        seconds,
        ...(error === undefined ? {} : { error }),
      });
      this.log(
        `step ${name}: ${status} in ${String(seconds)}s${error === undefined ? "" : `: ${error}`}`,
      );
    };
    try {
      const value = await fn(stepDir);
      await this.afterStep(stepDir);
      finish("passed");
      return value;
    } catch (error) {
      await this.afterStep(stepDir).catch(() => undefined);
      finish("failed", error instanceof Error ? error.message : String(error));
      throw error;
    }
  }
}
