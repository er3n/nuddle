# e2e

Real flows on two emulators against the local backend in E2E mode (`docs/testing.md`, E2E; ADR 0012).

| Folder | What |
|---|---|
| `scripts/emulators.sh` | Creates and boots the two AVDs headless (`create`, `boot [--cold]`, `stop`) |
| `orchestrator/` | TypeScript runner: backend, database, build, install, Maestro flows, test endpoints, run folder |
| `flows/` | Maestro YAML per device role |
| `fixtures/` | What the fixture `TaskExtractor` returns in E2E mode |

## Run

```
mise run e2e:smoke            # the smoke set; prints the run folder first and last
```

It runs in the foreground with a 20 minute hard timeout and exits non-zero on failure. It takes several minutes (first Gradle build, emulator boot), so agents start it in the background and poll `<run folder>/orchestrator.log` (CLAUDE.md, long-running commands).

Before the first run:
- `apps/web/.env.local` has `FIREBASE_SERVICE_ACCOUNT`: the service account key of Firebase project `nuddle-app-er3n` as one-line JSON. Pokes reach the device through a real FCM push, then a sync. The run stops at once without it.
- `google-services.json` for `app.nuddle.debug` is in `apps/android/app/src/e2e/` (git-ignored).
- Port 3000 is free: stop `mise run dev`. The run starts its own backend.
- About 6 GB of free RAM for the two emulators next to Docker and Gradle.

## Devices

| Serial | AVD | Role |
|---|---|---|
| `emulator-5554` | `nuddle-selin` | Selin |
| `emulator-5556` | `nuddle-eren` | Eren |

Image `system-images;android-36;google_apis_playstore;arm64-v8a` (stock Android with Google Play, for FCM). The hardware profile matches a Galaxy S23: 1080 x 2340 px, 480 dpi, about 360 x 780 dp, 2 GB RAM. Emulators stay up after a run and are reused; `mise run e2e:emulators:stop` shuts them down. `--cold` (`pnpm --filter @nuddle/e2e smoke --cold`) wipes their data first.

## Clean state

Every run:
- drops and re-creates the `nuddle_e2e` database from `supabase/migrations/` (same local Supabase stack, separate database; no seed),
- starts `next dev` with `E2E=1` against it,
- builds `assembleE2eDebug`, uninstalls and reinstalls `app.nuddle.debug` on both emulators with runtime permissions granted (`-g`) and `USE_FULL_SCREEN_INTENT` allowed (the setup screens 2c to 2e arrive in slice 1),
- clears logcat,
- after sign-up moves Selin's quiet hours to a one hour window 12 hours away, so the smoke plans pokes at any time of day (the default 23:00-07:00 would leave a night run with an empty plan).

Auth users live in the stack's Auth service and are not deleted; the app sees a new person because `people` is empty.

## Run folder

`e2e-runs/<UTC timestamp>/` (git-ignored):

| File | Content |
|---|---|
| `result.json` | `passed`, `failed` or `timed out`, the error, each step with its duration |
| `orchestrator.log` | What the orchestrator did, with timestamps |
| `server.log` | The backend's output |
| `logcat-selin.txt`, `logcat-eren.txt` | Logcat of both devices from install on |
| `gradle.log`, `emulators.log` | Build and emulator boot |
| `steps/NN-<step>/` | `selin.png` and `eren.png` after the step (passed or failed); Maestro's `maestro.log`, screenshots and hierarchy for flow steps |

## Smoke set

| # | Flow | State |
|---|---|---|
| 1 | Sign up with a test number, a task to self, the full screen poke rings, "Bitti", the task is done | `orchestrator/src/smoke/task-to-self.ts` |
| 2 to 6 | `docs/testing.md`, Smoke set | arrive with their feature slices |

Smoke 1 until slice 1: the task is created through the API (`create_task`), not in the app (NUD-10), and "the task leaves the list" is checked on the server (status `DONE`, no open task in sync) and on the device (no pending poke alarm), because home has no task list yet.

Plans are compressed (offsets divided by 60), so a deadline 90 minutes away rings the `FULL_SCREEN` step about 75 s after the task is created. Quiet hours still apply: Selin's default is 23:00 to 07:00 Istanbul time, so a run whose poke window falls into it gets no plan and fails at `plan-on-device`.
