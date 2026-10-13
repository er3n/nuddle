# 0017 The LLM plans the pokes, the server enforces the rules

Status: Accepted (2026-10-13). Supersedes the "pure function" part of ADR 0005 (who makes the plan); where and how the plan runs is unchanged. Decided in NUD-42.

## Context
Poke plan v0 (`planPokes`, open-questions #18) treats every time as a deadline and spreads a fixed ladder over the hour before it. The first phone test showed where that breaks: "2 dakika sonra su iç" means "remind me at 18:42", not "done by 18:42", and v0 poked at the moment of writing. A fixed ladder also cannot use what the product promises (docs/product.md): learning how each person responds. What the text means (a medicine at 9, a bill by Friday, a chore for a spouse who always answers the second poke) is exactly what a language model is good at and a hand-written heuristic is not.

## Decision
- **The planner is an LLM call behind a `PokePlanner` port**, separate from `TaskExtractor`, using the same provider and model setting (OpenRouter, `OPENROUTER_MODEL`).
- **Context it gets:** the task (raw text, title, detail, finish mode, timing and time), `now` and the recipient's time zone and weekday, quiet hours, the reachability level, whether the human step is allowed, the sender and the recipient with their relationship in both directions, the recipient's other connections (names and relationships), and the recipient's **history**: their recent tasks (title, from whom, relationship, timing, how it ended, which pokes rang at which level, which one woke them, how long after it they acted, snoozes, can't today). Recent means the last 30 closed tasks of the last 60 days (open-questions #42). This history is what makes the plan learn; it is assembled by the server from `task_events` and `poke_steps`, not stored separately.
- **Output:** JSON, a list of `{ at, level }` steps (`SILENT`, `SOUND`, `FULL_SCREEN`, `HUMAN`) and one short reason for logs and evals (never shown to users).
- **The server enforces the hard rules** in a pure function (`checkPlan`): steps in time order, inside the allowed window (not before `now`, not after the deadline), not inside quiet hours, escalation never drops, `SILENT` is never the only step, at most one `HUMAN` step and only when allowed, at most 8 device steps and at least 3 minutes between two of them. A step that breaks a rule is dropped; a plan with no valid device step is rejected.
- **v0 stays as the fallback and the first plan.** Writing a task stores the v0 plan at once (the device is armed within a second, as today), then the planner runs after the response (`after()` in Next.js) and, when its plan passes the check, replaces v0 as the next plan version. A planner error, timeout (10 s) or rejected plan keeps v0. The same happens on every regeneration (snooze end, re-request, new deadline, a setting the plan depends on). A task never waits for the LLM and never stays without a plan.
- **Timing: "at" or "by".** The extractor also returns `timing`. `by` ("9'a kadar", "akşama kadar", "2 saat içinde") is today's deadline. `at` ("2 dakika sonra", "9'da ilacını iç", "hatırlat") is a moment, and no poke may come before it. For an `at` task the extractor also says how long after the moment the task still makes sense (`missAfterMinutes`: a glass of water 30 minutes, a medicine 2 hours, a bill the rest of the day); the server keeps it between 15 minutes and 12 hours and uses 60 when it is missing (open-questions #43). The deadline is the moment plus that; it is when the task becomes missed. The preview shows "18:42'de" or "18:42'ye kadar" and the sender can switch it. `tasks.timing` stores it; v0 handles `at` by starting its ladder at the moment instead of ending at it.
- **Evals:** the golden set gets plan cases (an `at` task has no step before its moment; a medicine at 9 starts at 9; a person who always wakes on the second `SOUND` gets an early `SOUND`). Scenario and unit tests use a fixture planner, so they stay deterministic. E2E uses the fixture planner and compressed v0, as today.

## Consequences
- The plan can follow meaning and history without new code for every pattern. Better plans come from the prompt, the context and the model, measured by `mise run eval`.
- Pokes stay as reliable as before: the device rings from stored steps (ADR 0005), and every plan passes the same rule check. The LLM can make a plan worse, never unsafe.
- One more LLM call per task and per regeneration, after the response, so no added wait for the sender. Cost stays small at beta scale; the call is skipped when the task closes before it returns.
- The device may get two plan versions within seconds. It already ignores older versions.
- **Privacy:** past task titles, outcomes and connection names go to the LLM provider. The privacy notice must name this (docs/human-checklist.md, KVKK). Deleting an account deletes the history the planner reads.
- `planPokes` v0 stays in `apps/web/src/domain/plan-pokes.ts` as the fallback, with its scenario tests.

## Rejected
- **Keep the fixed heuristic and tune it:** cannot tell "at" from "by" or use history without growing into a hand-written model.
- **Let the LLM plan without a rule check:** one bad answer could poke inside quiet hours or never ring. The rules are the product's promise, so they stay in code.
- **Wait for the LLM before arming the device:** adds seconds to every task and a single point of failure; v0 first, then the better plan, gives both.
- **Plan in the extraction call:** the recipient, and so their history, is known only after extraction.
- **Learned statistics stored on edges first, LLM later:** more code before any learning; the LLM can read raw history today, and stored weights can be added later as more context.
