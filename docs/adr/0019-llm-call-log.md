# 0019 Every LLM call is kept with its input, answer and outcome

Status: Accepted (2026-10-15).

## Context
The first review of production (2026-10-15) found that the planner fell back to plan v0 on 34 of 42 tasks, and nothing said why: Vercel's Hobby plan keeps runtime logs for one hour, and the database had the task and its events but not what the model was given or what it answered. Judging the plans, and turning real use into eval cases, needs exactly that record. The person asked for it to be kept (2026-10-15): "bu data bizim için golden".

## Decision
- **Table `llm_calls`**, one row per call to the extractor or the planner: kind, the person (the writer for an extraction, the recipient for a plan), the task once there is one, the `write_task` key, the plan version asked for and the one adopted, the structured input, the model, the system prompt and the user prompt as sent, the answer (parsed JSON, or the text when it was not JSON), the outcome, the error, and the latency.
- **Outcomes.** Extraction: `create`, `ask`, `chat`, `failed`. Plan: `adopted`, `rejected` (checkPlan left no device step), `stale` (the task moved on while the model answered), `failed` (error or timeout). An extraction that asked is linked to the task once the preview creates it with the same text within the hour.
- **The ports report the exchange** (`ExchangeSink` on `TaskExtractor` and `PokePlanner`); the services write the row. Writing the log never fails the request it describes.
- **Private.** RLS on and no grants, like every table (ADR 0002). Deleting an account deletes the person's rows.
- **Export.** `mise run golden:export` reads production in a read-only session and writes one JSON line per task (the task, its LLM calls, every plan version with what rang, its events) to `golden/` at the repo root, which git ignores. Cases for `evals/` are written from it by hand, with names and texts changed where needed: the repo is public.

## Rejected
- **Logs only** (console or a log drain): an hour on Hobby, not queryable next to tasks, and a paid drain for a two-person beta.
- **Storing only the plan's reason:** without the input the decision cannot be judged or replayed.
- **A view in the database:** Supabase grants new views to the API roles by default, which breaks deny-all; the export query lives in the script instead.
