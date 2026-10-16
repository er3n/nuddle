// `mise run golden:export` (ADR 0019): one JSON line per task with its whole story, for building
// eval cases from real use: what was written, what the LLM was given and answered, every plan
// version with what rang, the edits and how it ended. Read-only: the session refuses writes.
// The output holds private texts: it goes to golden/ at the repo root, which git ignores.
import { mkdir, readFile, writeFile } from "node:fs/promises";
import postgres from "postgres";

const urlFile = process.env.GOLDEN_DB_URL_FILE;
const url = urlFile ? (await readFile(urlFile, "utf8")).trim() : process.env.DATABASE_URL;
if (!url) throw new Error("Set GOLDEN_DB_URL_FILE or DATABASE_URL");

const sql = postgres(url, {
  max: 1,
  prepare: false,
  connection: { default_transaction_read_only: true, TimeZone: "UTC" },
});

const rows = await sql<{ story: unknown }[]>`
  select json_build_object(
    'task', json_build_object(
      'id', t.id, 'writtenAt', t.created_at, 'rawText', t.raw_text, 'title', t.title,
      'detail', t.detail, 'timing', t.timing, 'moment', t.remind_at, 'deadline', t.deadline,
      'finishMode', t.finish_mode, 'status', t.status, 'late', t.late, 'planVersion', t.plan_version,
      'sender', ps.display_name, 'recipient', pr.display_name,
      'self', t.sender_id = t.recipient_id,
      'relationship', (select e.type from edges e where e.from_person_id = t.sender_id and e.to_person_id = t.recipient_id)
    ),
    'llmCalls', coalesce((
      select json_agg(json_build_object(
        'kind', c.kind, 'at', c.created_at, 'outcome', c.outcome, 'error', c.error,
        'planVersion', c.plan_version, 'adoptedVersion', c.adopted_version, 'model', c.model,
        'latencyMs', c.latency_ms, 'input', c.input, 'prompt', c.prompt, 'output', c.output,
        'promptTokens', c.prompt_tokens, 'completionTokens', c.completion_tokens,
        'costUsd', c.cost_usd
      ) order by c.created_at)
      from llm_calls c where c.task_id = t.id), '[]'),
    'plans', coalesce((
      select json_agg(json_build_object(
        'version', s.plan_version, 'seq', s.seq, 'level', s.level, 'plannedAt', s.planned_at,
        'firedAt', s.fired_at
      ) order by s.plan_version, s.seq)
      from poke_steps s where s.task_id = t.id), '[]'),
    'events', coalesce((
      select json_agg(json_build_object('type', e.type, 'at', e.created_at, 'payload', e.payload)
        order by e.id)
      from task_events e where e.task_id = t.id), '[]')
  ) as story
  from tasks t
  join people ps on ps.id = t.sender_id
  join people pr on pr.id = t.recipient_id
  order by t.created_at`;
await sql.end();

const dir = new URL("../../../golden/", import.meta.url);
await mkdir(dir, { recursive: true });
const name = `stories-${new Date().toISOString().replace(/[:.]/g, "-")}.jsonl`;
await writeFile(new URL(name, dir), rows.map((r) => JSON.stringify(r.story)).join("\n") + "\n");
console.info(`${String(rows.length)} task stories → golden/${name}`);
