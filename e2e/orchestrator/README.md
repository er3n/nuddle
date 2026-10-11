# e2e/orchestrator

TypeScript runner for the E2E sets (`docs/testing.md`, Orchestrator). Start it with `mise run e2e:smoke`; `../README.md` has the prerequisites and the run folder layout.

| File | What |
|---|---|
| `src/run.ts` | Entry: run folder, hard timeout, setup steps, the set, `result.json` |
| `src/context.ts` | The run folder, the log and steps with timings and screenshots |
| `src/backend.ts` | Local Supabase, the `nuddle_e2e` database, `next dev` with `E2E=1` |
| `src/devices.ts` | adb and Maestro per emulator (Selin `emulator-5554`, Eren `emulator-5556`) |
| `src/api.ts` | The API typed from `packages/contract`, and the `/api/test/*` endpoints |
| `src/smoke/` | One file per smoke flow |
