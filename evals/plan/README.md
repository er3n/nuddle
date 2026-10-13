# Poke plan golden set

`golden.json`: cases for the LLM poke planner (ADR 0017, docs/testing.md, LLM eval). Each case is a planner input (times in the recipient's time zone) and the properties the checked plan must have. A plan is scored after `checkPlan`, as the app would use it:

- `valid`: `checkPlan` keeps a plan (otherwise v0 would be used).
- `nothingBeforeTime`, `firstWithinMinutesOfTime`: for `at` tasks, no poke before the moment and the first one right at it.
- `lastDeviceAtLeastMinutesBeforeDeadline`: time left to do the task after the last device poke.
- `reachesLevel`, `firstLevelAtLeast`, `maxDeviceSteps`, `fullScreenAtLeastMinutesBeforeDeadline`: how the plan follows the history.

Run with `mise run eval` (with the extraction set).
