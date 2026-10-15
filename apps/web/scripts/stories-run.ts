// `mise run stories` (NUD-70): runs evals/stories/sentences.jsonl through the real extractor and
// planner with a fixed cast and clock, as the app would, and writes one JSON line per sentence
// with what was understood, plan v0, the model's proposal, what checkPlan kept, and latencies.
// Never part of CI. Output: the directory given as the first argument (default evals/stories/out,
// git-ignored).
import { existsSync, readFileSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { parseEnv } from "node:util";
import { resolveExtraction, type Extraction, type ExtractionInput } from "../src/domain/extraction";
import { checkPlan, type PlannerInput } from "../src/domain/planner";
import { planPokes } from "../src/domain/plan-pokes";
import { scheduleOf } from "../src/domain/timing";
import type { EdgeType, PokeStep, QuietHours } from "../src/domain/types";
import { OpenRouterExtractor } from "../src/infra/llm/openrouter-extractor";
import { OpenRouterPlanner } from "../src/infra/llm/openrouter-planner";

const envPath = new URL("../.env.local", import.meta.url);
function setting(name: string): string | undefined {
  const fromEnv = process.env[name];
  if (fromEnv) return fromEnv;
  if (!existsSync(envPath)) return undefined;
  return parseEnv(readFileSync(envPath, "utf8"))[name];
}
const apiKey = setting("OPENROUTER_API_KEY");
const model = setting("OPENROUTER_MODEL");
if (!apiKey || !model) throw new Error("Set OPENROUTER_API_KEY and OPENROUTER_MODEL");

const TZ = "Europe/Istanbul";
const quietHours: QuietHours = { start: "23:00", end: "07:00", timeZone: TZ };

/** The cast: who each person can write to, and what that person is to them. */
const cast: Record<string, { name: string; relationship: EdgeType }[]> = {
  Sibel: [
    { name: "Eren", relationship: "spouse" },
    { name: "Bahar", relationship: "child" },
    { name: "Ayşe", relationship: "parent" },
    { name: "Can", relationship: "sibling" },
  ],
  Eren: [
    { name: "Sibel", relationship: "spouse" },
    { name: "Bahar", relationship: "child" },
  ],
  Bahar: [
    { name: "Sibel", relationship: "parent" },
    { name: "Eren", relationship: "parent" },
  ],
  Ayşe: [{ name: "Sibel", relationship: "child" }],
  Can: [{ name: "Sibel", relationship: "sibling" }],
};
const back: Record<EdgeType, EdgeType> = {
  spouse: "spouse",
  parent: "child",
  child: "parent",
  sibling: "sibling",
  roommate: "roommate",
  other: "other",
};

interface Sentence {
  id: string;
  group: string;
  sender: string;
  now: string;
  text: string;
}

const sentencesPath = new URL("../../../evals/stories/sentences.jsonl", import.meta.url);
const sentences = readFileSync(sentencesPath, "utf8")
  .split("\n")
  .filter((l) => l.trim() !== "")
  .map((l) => JSON.parse(l) as Sentence);

const outDir = process.argv[2] ?? new URL("../../../evals/stories/out/", import.meta.url).pathname;
await mkdir(outDir, { recursive: true });

const extractor = new OpenRouterExtractor({ apiKey, model });
const planner = new OpenRouterPlanner({ apiKey, model, timeoutMs: 60_000 });

const fmt = (d: Date) =>
  new Intl.DateTimeFormat("en-GB", {
    timeZone: TZ,
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
  }).format(d);
const stepsText = (steps: readonly { level: string; plannedAt: Date }[]) =>
  steps.map((s) => `${s.level}@${fmt(s.plannedAt)}`).join(", ");

async function runOne(s: Sentence): Promise<Record<string, unknown>> {
  const now = new Date(s.now);
  const connections = cast[s.sender] ?? [];
  const input: ExtractionInput = {
    text: s.text,
    now,
    timeZone: TZ,
    senderName: s.sender,
    connections,
  };
  const out: Record<string, unknown> = { ...s, nowLocal: fmt(now) };
  let extraction: Extraction;
  const t0 = performance.now();
  try {
    extraction = await extractor.extract(input);
  } catch (error) {
    out.error = `extraction: ${String(error)}`;
    return out;
  }
  out.extractionMs = Math.round(performance.now() - t0);
  out.extraction = extraction;
  const resolved = resolveExtraction(
    extraction,
    { id: s.sender },
    connections.map((c) => ({ personId: c.name, name: c.name })),
    now,
    s.text,
  );
  out.resolution =
    resolved.kind === "create"
      ? "create"
      : resolved.kind === "ask"
        ? `ask ${resolved.question}`
        : "chat";
  if (resolved.kind === "chat") out.reply = resolved.reply;
  if (resolved.kind !== "create") return out;

  const self = resolved.recipientId === s.sender;
  const recipient = resolved.recipientId;
  const schedule = scheduleOf(resolved.timing, resolved.deadline, resolved.missAfterMinutes);
  const task = {
    rawText: s.text,
    title: resolved.title,
    detail: resolved.detail,
    finishMode: resolved.finishMode,
    timing: resolved.timing,
    time: resolved.deadline,
    deadline: schedule.deadline,
    notBefore: now,
    floor: "SILENT" as const,
    snoozesSoFar: 0,
  };
  out.task = {
    title: task.title,
    detail: task.detail,
    to: recipient,
    self,
    timing: task.timing,
    finishMode: task.finishMode,
    time: fmt(task.time),
    deadline: fmt(task.deadline),
    missAfterMinutes: resolved.missAfterMinutes,
  };
  const senderEdge = cast[s.sender]?.find((c) => c.name === recipient) ?? null;
  const rules = {
    task: {
      deadline: task.deadline,
      timing: task.timing,
      remindAt: schedule.remindAt,
      senderId: s.sender,
      recipientId: recipient,
    },
    recipient: { quietHours, useConnectionsForMyTasks: true },
    sender: { notifyMeForOthersTasks: true },
    senderEdge: self ? null : { notifyMe: true },
    reachability: "FULL_SCREEN" as const,
    history: [],
    now,
  };
  const v0 = planPokes(rules);
  out.v0 = stepsText(v0);

  const plannerInput: PlannerInput = {
    now,
    timeZone: TZ,
    task,
    sender: { name: s.sender, self },
    recipient: { name: recipient },
    relationship:
      self || !senderEdge
        ? null
        : {
            recipientIsToSender: senderEdge.relationship,
            senderIsToRecipient: back[senderEdge.relationship],
          },
    recipientConnections: cast[recipient] ?? [],
    quietHours,
    reachability: "FULL_SCREEN",
    humanAllowed: !self,
    profile: null,
    history: [],
  };
  const t1 = performance.now();
  try {
    const proposal = await planner.plan(plannerInput);
    out.plannerMs = Math.round(performance.now() - t1);
    out.proposal = stepsText(proposal.steps.map((p) => ({ level: p.level, plannedAt: p.at })));
    out.reason = proposal.reason;
    const kept: PokeStep[] | null = checkPlan(proposal, {
      ...rules,
      notBefore: now,
      floor: "SILENT",
    });
    out.plan = kept ? stepsText(kept) : null;
    out.dropped = proposal.steps.length - (kept?.length ?? 0);
  } catch (error) {
    out.plannerMs = Math.round(performance.now() - t1);
    out.error = `planner: ${String(error)}`;
  }
  return out;
}

const results: Record<string, unknown>[] = [];
const queue = [...sentences];
async function worker(): Promise<void> {
  for (let s = queue.shift(); s; s = queue.shift()) {
    const r = await runOne(s);
    results.push(r);
    const plan = r.plan ?? r.resolution;
    console.log(
      `${String(r.id).padEnd(26)} ${String(plan)}${typeof r.error === "string" ? `  ERROR ${r.error}` : ""}`,
    );
  }
}
await Promise.all(Array.from({ length: 5 }, worker));
results.sort(
  (a, b) => sentences.findIndex((s) => s.id === a.id) - sentences.findIndex((s) => s.id === b.id),
);
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
await writeFile(
  `${outDir}/run-${stamp}.jsonl`,
  results.map((r) => JSON.stringify(r)).join("\n") + "\n",
);
console.log(`${String(results.length)} sentences → ${outDir}/run-${stamp}.jsonl`);
