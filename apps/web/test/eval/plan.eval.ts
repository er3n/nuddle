// LLM poke plan eval (ADR 0017, docs/testing.md, LLM eval). Runs evals/plan/golden.json against
// the real provider, checks each answer with `checkPlan` as the app does, and prints a score.
// Run with `mise run eval`; never part of CI.
import { existsSync, readFileSync } from "node:fs";
import { parseEnv } from "node:util";
import { expect, it } from "vitest";
import { checkPlan, type HistoryItem, type PlannerInput } from "../../src/domain/planner";
import { MINUTE } from "../../src/domain/time";
import type { DeviceLevel, PokeStep } from "../../src/domain/types";
import { OpenRouterPlanner } from "../../src/infra/llm/openrouter-planner";

type Json<T> = {
  [K in keyof T]: T[K] extends Date ? string : T[K] extends Date | null ? string | null : T[K];
};

interface GoldenCase {
  id: string;
  now: string;
  timeZone: string;
  task: Omit<Json<PlannerInput["task"]>, "startAt" | "leadMinutes"> &
    Partial<Pick<Json<PlannerInput["task"]>, "startAt" | "leadMinutes">>;
  sender: PlannerInput["sender"];
  recipient: PlannerInput["recipient"];
  relationship: PlannerInput["relationship"];
  recipientConnections: PlannerInput["recipientConnections"];
  quietHours: PlannerInput["quietHours"];
  reachability: PlannerInput["reachability"];
  humanAllowed: boolean;
  /** How the person responds to pokes (ADR 0020); missing in cases written before it. */
  profile?: string | null;
  history: (Omit<Json<HistoryItem>, "pokes"> & {
    pokes: { level: HistoryItem["pokes"][number]["level"]; plannedAt: string; firedAt: string }[];
  })[];
  expect: {
    valid: boolean;
    nothingBeforeTime?: boolean;
    firstWithinMinutesOfTime?: number;
    lastDeviceAtLeastMinutesBeforeDeadline?: number;
    reachesLevel?: DeviceLevel;
    firstLevelAtLeast?: DeviceLevel;
    maxDeviceSteps?: number;
    fullScreenAtLeastMinutesBeforeDeadline?: number;
  };
}

const goldenPath = new URL("../../../../evals/plan/golden.json", import.meta.url);
const envPath = new URL("../../.env.local", import.meta.url);

function setting(name: string): string | undefined {
  const fromEnv = process.env[name];
  if (fromEnv) return fromEnv;
  if (!existsSync(envPath)) return undefined;
  return parseEnv(readFileSync(envPath, "utf8"))[name];
}

const rank: Record<DeviceLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2 };

function toInput(c: GoldenCase): PlannerInput {
  return {
    now: new Date(c.now),
    timeZone: c.timeZone,
    task: {
      ...c.task,
      startAt: new Date(c.task.startAt ?? c.task.time),
      leadMinutes: c.task.leadMinutes ?? 0,
      time: new Date(c.task.time),
      deadline: new Date(c.task.deadline),
      notBefore: new Date(c.task.notBefore),
    },
    sender: c.sender,
    recipient: c.recipient,
    relationship: c.relationship,
    recipientConnections: c.recipientConnections,
    quietHours: c.quietHours,
    reachability: c.reachability,
    humanAllowed: c.humanAllowed,
    profile: c.profile ?? null,
    history: c.history.map((h) => ({
      ...h,
      time: new Date(h.time),
      deadline: new Date(h.deadline),
      actedAt: h.actedAt === null ? null : new Date(h.actedAt),
      pokes: h.pokes.map((p) => ({
        level: p.level,
        plannedAt: new Date(p.plannedAt),
        firedAt: new Date(p.firedAt),
      })),
    })),
  };
}

function score(
  c: GoldenCase,
  input: PlannerInput,
  steps: PokeStep[] | null,
): Record<string, boolean> {
  const e = c.expect;
  const result: Record<string, boolean> = { valid: (steps !== null) === e.valid };
  if (!steps) return result;
  const device = steps.filter((s): s is PokeStep & { level: DeviceLevel } => s.level !== "HUMAN");
  const time = input.task.time.getTime();
  const deadline = input.task.deadline.getTime();
  const minutesBefore = (t: Date) => (deadline - t.getTime()) / MINUTE;
  if (e.nothingBeforeTime)
    result.nothingBeforeTime = steps.every((s) => s.plannedAt.getTime() >= time);
  if (e.firstWithinMinutesOfTime !== undefined) {
    const first = device[0];
    result.firstAtTime =
      first !== undefined &&
      Math.abs(first.plannedAt.getTime() - time) <= e.firstWithinMinutesOfTime * MINUTE;
  }
  if (e.lastDeviceAtLeastMinutesBeforeDeadline !== undefined) {
    const last = device.at(-1);
    result.timeToDoIt =
      last !== undefined &&
      minutesBefore(last.plannedAt) >= e.lastDeviceAtLeastMinutesBeforeDeadline;
  }
  if (e.reachesLevel !== undefined) {
    const target = e.reachesLevel;
    result.reachesLevel = device.some((s) => rank[s.level] >= rank[target]);
  }
  if (e.firstLevelAtLeast !== undefined) {
    const min = e.firstLevelAtLeast;
    result.firstLevel = device[0] !== undefined && rank[device[0].level] >= rank[min];
  }
  if (e.maxDeviceSteps !== undefined) result.fewSteps = device.length <= e.maxDeviceSteps;
  if (e.fullScreenAtLeastMinutesBeforeDeadline !== undefined) {
    const firstFull = device.find((s) => s.level === "FULL_SCREEN");
    result.fullScreenEarly =
      firstFull !== undefined &&
      minutesBefore(firstFull.plannedAt) >= e.fullScreenAtLeastMinutesBeforeDeadline;
  }
  return result;
}

it("poke plan golden set", { timeout: 30 * 60_000 }, async () => {
  const apiKey = setting("OPENROUTER_API_KEY");
  const model = setting("OPENROUTER_MODEL");
  if (!apiKey || !model) {
    throw new Error("Set OPENROUTER_API_KEY and OPENROUTER_MODEL (env or apps/web/.env.local).");
  }
  const planner = new OpenRouterPlanner({ apiKey, model, timeoutMs: 60_000 });
  const cases = JSON.parse(readFileSync(goldenPath, "utf8")) as GoldenCase[];

  const failures: string[] = [];
  let perfect = 0;
  for (const c of cases) {
    const input = toInput(c);
    let steps: PokeStep[] | null;
    let reason: string;
    try {
      const proposal = await planner.plan(input);
      reason = proposal.reason;
      steps = checkPlan(proposal, {
        task: {
          deadline: input.task.deadline,
          timing: input.task.timing,
          remindAt: input.task.timing === "at" ? input.task.time : null,
          senderId: c.sender.self ? "r" : "s",
          recipientId: "r",
        },
        recipient: { quietHours: c.quietHours, useConnectionsForMyTasks: c.humanAllowed },
        sender: { notifyMeForOthersTasks: c.humanAllowed },
        senderEdge: { notifyMe: c.humanAllowed },
        notBefore: input.task.notBefore,
        floor: input.task.floor,
      });
    } catch (error) {
      failures.push(`${c.id}: planner failed: ${String(error)}`);
      continue;
    }
    const result = score(c, input, steps);
    const failed = Object.entries(result)
      .filter(([, ok]) => !ok)
      .map(([k]) => k);
    const plan =
      steps?.map((s) => `${s.level}@${s.plannedAt.toISOString().slice(11, 16)}Z`).join(" ") ??
      "none";
    console.log(`${failed.length === 0 ? "ok  " : "FAIL"} ${c.id}  ${plan}  (${reason})`);
    if (failed.length === 0) perfect++;
    else failures.push(`${c.id}: ${failed.join(", ")}  plan: ${plan}`);
  }

  console.log(`Model: ${model}`);
  console.log(`Plans fully correct: ${String(perfect)}/${String(cases.length)}`);
  if (failures.length > 0) console.log(`Failures:\n  ${failures.join("\n  ")}`);
  expect(cases.length).toBeGreaterThan(0);
});
