# Extraction golden set

`golden.jsonl`: one case per line for the LLM task extraction (docs/testing.md, LLM eval). Each case has the raw text, the sender's time zone, a fixed `now`, the sender's connections and the expected result. `question: true` means the preview must ask one question (recipient or deadline missing or ambiguous); then only `recipient` and `question` are scored. `timing` (`at` or `by`, ADR 0017) is scored only on cases that give it. `chat: true` means the text is not a task: only that a reply came is scored; on every other case a reply counts as wrong (NUD-52).

Run with `mise run eval` (needs `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` in the environment or in `apps/web/.env.local`). It prints a per-field score and the failing cases. Not part of CI.

Scored per case: recipient, question, and when no question is expected: deadline (same instant), finish mode and the digits of the detail. Titles are printed for review but not scored, since wording varies.
