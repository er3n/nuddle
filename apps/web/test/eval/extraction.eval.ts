// LLM extraction eval (docs/testing.md, LLM eval). Runs evals/extraction/golden.jsonl against the
// real provider and prints a score. Run with `mise run eval`; never part of CI.
import { existsSync, readFileSync } from "node:fs";
import { parseEnv } from "node:util";
import { expect, it } from "vitest";
import type { Extraction } from "../../src/domain/extraction";
import type { EdgeType, FinishMode } from "../../src/domain/types";
import { OpenRouterExtractor } from "../../src/infra/llm/openrouter-extractor";

interface GoldenCase {
  id: string;
  text: string;
  timeZone: string;
  now: string;
  sender: string;
  connections: { name: string; relationship: EdgeType }[];
  expected: {
    recipient: string | null;
    title: string | null;
    detail: string | null;
    deadline: string | null;
    finishMode: FinishMode | null;
    question: boolean;
  };
}

const goldenPath = new URL("../../../../evals/extraction/golden.jsonl", import.meta.url);
const envPath = new URL("../../.env.local", import.meta.url);

function setting(name: string): string | undefined {
  const fromEnv = process.env[name];
  if (fromEnv) return fromEnv;
  if (!existsSync(envPath)) return undefined;
  return parseEnv(readFileSync(envPath, "utf8"))[name];
}

const digits = (s: string | null): string => (s ?? "").replace(/\D/g, "");

function score(c: GoldenCase, got: Extraction): Record<string, boolean> {
  const question = got.recipient === null || got.missing.length > 0 || got.deadline === null;
  const result: Record<string, boolean> = {
    recipient: (got.recipient ?? null) === c.expected.recipient,
    question: question === c.expected.question,
  };
  if (!c.expected.question) {
    result.deadline =
      got.deadline !== null &&
      c.expected.deadline !== null &&
      new Date(got.deadline).getTime() === new Date(c.expected.deadline).getTime();
    result.finishMode = got.finishMode === c.expected.finishMode;
    result.detail = digits(got.detail) === digits(c.expected.detail);
  }
  return result;
}

it("extraction golden set", { timeout: 30 * 60_000 }, async () => {
  const apiKey = setting("OPENROUTER_API_KEY");
  const model = setting("OPENROUTER_MODEL");
  if (!apiKey || !model) {
    throw new Error("Set OPENROUTER_API_KEY and OPENROUTER_MODEL (env or apps/web/.env.local).");
  }
  const extractor = new OpenRouterExtractor({ apiKey, model });
  const cases = readFileSync(goldenPath, "utf8")
    .split("\n")
    .filter((line) => line.trim() !== "")
    .map((line) => JSON.parse(line) as GoldenCase);

  const totals = new Map<string, { pass: number; total: number }>();
  const failures: string[] = [];
  let perfect = 0;

  for (const c of cases) {
    let got: Extraction;
    try {
      got = await extractor.extract({
        text: c.text,
        now: new Date(c.now),
        timeZone: c.timeZone,
        senderName: c.sender,
        connections: c.connections,
      });
    } catch (error) {
      failures.push(`${c.id}: error ${String(error)}`);
      continue;
    }
    const fields = score(c, got);
    for (const [field, ok] of Object.entries(fields)) {
      const t = totals.get(field) ?? { pass: 0, total: 0 };
      totals.set(field, { pass: t.pass + (ok ? 1 : 0), total: t.total + 1 });
    }
    const wrong = Object.entries(fields).filter(([, ok]) => !ok);
    if (wrong.length === 0) perfect++;
    else {
      failures.push(
        `${c.id}: wrong ${wrong.map(([f]) => f).join(", ")}\n    text: ${c.text}\n    got:  ${JSON.stringify(got)}`,
      );
    }
    console.log(`${wrong.length === 0 ? "ok  " : "FAIL"} ${c.id}  title: ${String(got.title)}`);
  }

  const lines = [
    `Model: ${model}`,
    `Cases fully correct: ${String(perfect)}/${String(cases.length)} (${String(Math.round((100 * perfect) / cases.length))}%)`,
    ...[...totals].map(
      ([field, t]) => `  ${field.padEnd(10)} ${String(t.pass)}/${String(t.total)}`,
    ),
    ...(failures.length > 0 ? ["Failures:", ...failures.map((f) => `  ${f}`)] : []),
  ];
  console.log(lines.join("\n"));
  expect(cases.length).toBeGreaterThan(0);
});
