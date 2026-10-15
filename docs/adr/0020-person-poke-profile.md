# 0020 The planner reads a rolling person profile plus a short recent history

Status: Accepted (2026-10-15). Amends the "history" part of ADR 0017; everything else in 0017 stands.

## Context
ADR 0017 gives the planner the recipient's last 30 closed tasks in full (open-questions #42). That is bounded, but it is the wrong shape: for someone with many tasks the prompt is long and mostly repetition, for someone with a long history everything older than 60 days is forgotten, and the model re-derives "this person answers the second SOUND" on every call. The person asked (2026-10-15) for a summary that grows over time instead of raw context that may blow up.

## Decision
- **A person profile:** a short text (at most about 120 words, English, never shown to users) on how this person responds to pokes: which level usually gets them to act and how long after it, whether they snooze or drop, differences by sender, time of day or kind of task, and anything the plan should know. Stored on `people` (`poke_profile`, `poke_profile_at`, `poke_profile_tasks`: the number of closed tasks the profile had seen).
- **Written by the LLM, in the background, right after a plan.** When `refinePlan` runs for a task of this recipient, it plans with the profile there is; when that profile is stale, it then asks the model for a new one from the previous profile plus the last 30 closed tasks of the last 60 days and stores it for the next plan. Stale means: 10 or more closed tasks since the profile was written, or the profile is older than 7 days and at least one task closed since. Nothing happens on the request path; a failure keeps the old profile (or none). The profiler runs with thinking off and a 20 s timeout. (Amended 2026-10-15, NUD-71: writing it before the plan delayed the pokes by up to 30 s in production.)
- **The planner's context becomes:** the profile (or null) plus the **last 8 closed tasks** in full, in the shape 0017 defines. The profile carries the long memory; the 8 tasks carry what just happened.
- **Logged** like every call: `llm_calls.kind = profile` with the input, the prompt and the answer (ADR 0019).
- **Privacy:** the profile is derived from the same data 0017 already sends to the provider. Deleting the account clears it. It is never shown to the person or to a sender.

## Consequences
- Prompt size no longer grows with use. Long-term patterns survive the 60-day window.
- At most one extra LLM call per 10 closed tasks per person, in the background.
- Evals: the plan golden set gets cases with a profile; `historyItem` and `checkPlan` are unchanged.

## Rejected
- **Regenerate the profile on every task close** (an effect of the close): more plumbing through every close path and the tick, for a summary nobody reads until the next plan.
- **Statistics instead of a text** (counts per level and sender): loses the "why"; the model reads a sentence better than a table, and the text can say things a fixed schema cannot.
- **Keep sending 30 tasks and also the profile:** the profile is meant to replace the bulk, not add to it.
