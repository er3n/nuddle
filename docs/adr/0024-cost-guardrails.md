# 0024 One person cannot run up the bill: daily caps on LLM calls and SMS, and every call's cost is kept

Status: Accepted (2026-10-16).

## Context
The fixed costs are small (about 45 USD a month at launch) and a normal person costs cents: two LLM calls per task (extraction and poke plan, ADR 0017) and an SMS per sign-in (0.0305 USD to Turkey, open-questions #45). What can hurt is usage nobody bounded: a client or script that writes texts in a loop (each one an extraction, and a plan for the recipient), and SMS pumping on the sign-in code. Before this ADR (NUD-97 audit) there was no cap on LLM calls at all, and for SMS only the 3-wrong-codes wait (#41), the Turkish-numbers-only rule (#45) and Supabase Auth's project-wide hourly SMS limit, whose cloud value was not in code. `llm_calls` (ADR 0019) kept no token counts, so the cost of a task, which the Premium price needs (#10), was unknown.

## Decision
- **Rolling 24 hour caps, counted from tables we already write.** Each cap counts the rows of the last 24 hours (the injected clock, ADR 0009), so no counter table and no reset job. The numbers are defaults in `apps/web/src/domain/cost-caps.ts` (open-questions #54); a real person should never meet one.
- **LLM, per person, from `llm_calls`.** Extractions per writer, plans per recipient, profiles per person, each with its own cap. An extraction over the cap is refused before the model is called: `write_limit` (DRT-1010, 429) with `retryAt`. A plan or profile over the cap is not asked for: plan v0 stays, as for any planner failure (ADR 0017), and the row is kept with the outcome `capped` so the log says why v0 stayed. `capped` rows do not count toward the cap.
- **SMS, per phone and in total, in the Send SMS hook.** A new table `sms_sends` keeps the phone and time of every code the hook handed to the provider. The hook is the only place a real SMS leaves, so the caps hold even for a sign-in request that reaches Supabase Auth without our API. Over a cap the hook answers 429 in the shape Supabase Auth expects and sends nothing. `/v1/auth/otp` checks the same caps first so the app gets `otp_limit` (DRT-1009, 429, with `retryAt`) instead of `upstream_failed`. Rows older than the window are deleted when a new code is sent, and deleting an account deletes its phone's rows, like `otp_attempts`. Test numbers never reach the hook and are never capped.
- **The Supabase Auth limits are code.** `supabase/cloud/auth.json` sets `rate_limit_sms_sent` (per hour, whole project) and `sms_max_frequency` (seconds between codes to one phone), applied by `configure.sh auth`.
- **Every call's tokens and cost are kept** (extends ADR 0019). The OpenRouter request asks for usage accounting; `llm_calls` gets `prompt_tokens`, `completion_tokens` and `cost_usd` (what OpenRouter charged, null when the answer did not say). The cost of a task is the sum over its rows; `golden:export` carries them.
- **The last line is outside the code.** A prepaid OpenRouter credit with no auto top-up, Twilio Geo permissions to Turkey only with no auto-recharge, and Vercel spend management bound the worst case even if the code is wrong. They are on the human checklist (`docs/human-checklist.md`) and in `architecture.md`, Costs.

## Consequences
- The app has no copy of its own for the two new codes yet: a refused write shows the failed-write state (6b, "Tekrar dene"), a refused code shows the failed-send message. Dedicated copy waits until someone meets a cap (open-questions #54).
- A count query runs before every extraction, plan, profile and SMS. Each is one indexed lookup.

## Rejected
- **A rate-limit service (Upstash, Vercel WAF rules):** another account and dashboard configuration (rule 5) for what one query on our own tables does.
- **Per-minute rate limiting instead of daily caps:** it bounds bursts, not a slow script running all day, which is what costs money.
- **Only the external limits:** a prepaid credit that runs out stops every user's pokes at once; the per-person cap stops only the one who runs up the bill.
- **A counter column per person:** needs a reset rule and a write on every call; the rows are already there.
