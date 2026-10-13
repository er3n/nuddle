# Bootstrap plan

Seven stages from an empty repo to a beta-ready skeleton. Give Claude Code **one stage per session**. Each stage ends when its "Done when" commands are green and a PR is open. The human merges.

Before stage 1, the human finishes the "Before stage 1" part of `human-checklist.md`.

Each stage and each feature slice has a Linear issue in the Nuddle workspace that points back to this file. Follow the ticket flow in `CLAUDE.md`.

Prompt template for each stage:

> Read `CLAUDE.md` and `docs/bootstrap-plan.md`, then do stage N (Linear issue NUD-xx). Read the docs listed under "Read first" before writing code. Stop and ask if a doc is unclear or contradicts another. Run long commands in the background with a timeout. Open a PR when "Done when" is green.

---

## Stage 1 · Repo foundation

**Read first:** `CLAUDE.md`, `docs/architecture.md` (Repository layout), `docs/quality.md`, ADR 0001.

**Tasks**
- Create the layout from `architecture.md` with placeholder READMEs where folders are empty.
- `mise.toml`: pin node 22, pnpm 10, java 21 (temurin), supabase CLI, maestro. Define every task listed in `CLAUDE.md` (stubs that print "not yet" are fine for later stages, but they must exist).
- pnpm workspace with `apps/web` (empty Next.js + TypeScript strict).
- ESLint (typescript-eslint strictTypeChecked + the rules in `quality.md`), Prettier, dependency-cruiser, knip, wired into `mise run lint` and `mise run fix`.
- lefthook (format + fast lint on staged files), gitleaks.
- `.github/workflows/ci.yml`: install mise, run `mise run lint` and `mise run test`. Cache pnpm and Gradle.
- `.claude/settings.json`: allow list and deny list from `quality.md` (Claude Code guardrails), and a post-edit hook that runs the formatter for the edited file type. Check the current Claude Code docs for the exact settings and hooks format.
- `.gitignore`, `.editorconfig`, Renovate config (weekly, grouped).

**Done when:** `mise run lint` passes locally and in CI on the first PR. A deliberately committed fake secret is caught by gitleaks (then removed).

## Stage 2 · Database

**Read first:** `docs/domain.md` (Entities), ADRs 0002, 0007, 0010, 0011.

**Tasks**
- `supabase init`. `config.toml`: disable Studio, Realtime, analytics and edge runtime; enable phone auth; add test phone numbers with fixed OTP codes (Selin `+905320000001`, Eren `+905320000002`, more for E2E).
- Drizzle schema in `apps/web/src/infra/db/schema.ts`: `people`, `edges`, `invites`, `tasks`, `task_events`, `poke_steps`, `scheduled_actions`, `devices`, `idempotency_keys`. `drizzle-kit generate` writes into `supabase/migrations/`.
- Hand-written SQL migration: revoke all table privileges from `anon` and `authenticated`, enable RLS with no policies (deny-all safety net).
- `supabase/seed.sql`: Selin and Eren, connected as spouses, one scheduled task.
- `squawk` on migrations in `mise run lint`.
- `mise run dev`: `supabase start` (if not running), `supabase db reset`, then `next dev`.
- Integration test helper: create a per-run database (`nuddle_test_<id>`) in the local Postgres, apply migrations, drop after.

**Done when:** `mise run dev` brings everything up from a clean machine; `supabase db reset` applies migrations and seed; one integration test inserts and reads a task.

## Stage 3 · Domain core and scenario harness

**Read first:** `docs/domain.md` (all), `docs/testing.md` (Scenario tests), ADRs 0005, 0007, 0009.

**Tasks**
- `src/infra/clock.ts` (real) and a `FakeClock` for tests.
- `src/domain`: task state machine (exhaustive), transition functions that return new state + events + scheduled actions, `planPokes` v0 (simple heuristic satisfying every constraint in `domain.md`; document the heuristic in code comments and in `domain.md`), quiet hours shifting, `tick(now)` handlers for each `scheduled_actions` kind, reachability derivation.
- Interfaces and fakes: `PushSender`, `OtpSender`, `TaskExtractor`, `PhotoStorage`.
- `TaskExtractor` fixture implementation keyed by input text. `evals/extraction/golden.jsonl` with ~30 cases and a `mise run eval` script against OpenRouter (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`; the real provider can be stubbed until a key exists).
- Scenario harness: two simulated people, injected clock, fake push recorder. Write the scenario list from `testing.md`.

**Done when:** `mise run test:fast` and `mise run test` pass; all scenarios in `testing.md` exist and pass; no `Date.now()` in `src/domain` (lint).

## Stage 4 · API, sync and contract

**Read first:** `docs/architecture.md` (Sync model, Server timers, E2E mode), ADRs 0003, 0006, 0008, 0011.

**Tasks**
- Hono app under `/api` with `@hono/zod-openapi`. Middleware: Supabase JWT verification, request id, error mapping to `DRT-xxxx` codes.
- Commands (`POST /api/v1/commands/<name>`) with idempotency keys for every recipient and sender action in `domain.md`. `GET /api/v1/sync?cursor=`.
- `POST /api/v1/devices` (Firebase Installation ID + permission summary, ADR 0015) and reachability.
- Photo flow: signed upload URL from Supabase Storage, deletion when a task closes.
- `/api/internal/tick` and `/api/internal/otp-hook` behind a shared secret. Real `OtpSender` (SMS provider first), real `PushSender` (`firebase-admin`).
- SQL migration with the `pg_cron` + `pg_net` job calling the tick endpoint (disabled locally, documented).
- E2E module: `/test/reset`, `/test/seed`, `/test/tick`, `/test/advance-clock`, compressed plans, fixture extractor, only when `E2E=1`; a test that they 404 otherwise.
- `mise run contract:gen` → `packages/contract/openapi.yaml`, redocly lint, CI diff check.
- Scenario tests switched to go through HTTP with a generated TS client.

**Done when:** `mise run test` passes including HTTP-level scenarios; the contract diff check fails when a schema changes without regenerating (prove once, then revert).

## Stage 5 · Android skeleton

**Read first:** `docs/ux/components.md`, `docs/ux/tokens.json`, `docs/ux/platform.md`, `docs/ux/screens.md` (routes, 0a to 0e, 1a), `docs/glossary.md`, `docs/quality.md` (Kotlin), ADRs 0004, 0005, 0008, 0009.

**Tasks**
- Gradle project in `apps/android`: `applicationId app.nuddle`, `namespace app.nuddle`, min SDK 26, flavors `dev`, `e2e`, `prod` (dev and e2e use `app.nuddle.debug`). Version catalog. Configuration cache on.
- Spotless/ktlint, detekt with Compose rules and the forbidden calls, Android Lint errors, Konsist tests, `allWarningsAsErrors`.
- OpenAPI Generator plugin producing the client from `packages/contract/openapi.yaml`.
- Hilt, Room (tasks, poke steps, outbox), WorkManager outbox worker, kotlinx-datetime `Clock` injection.
- Fonts: download Space Grotesk and IBM Plex Sans TTFs (OFL, from the google/fonts GitHub repo) into `res/font`.
- Theme from `tokens.json` (`DurtColors`, `DurtType`, `DurtDimens`), all components in `components.md` with previews.
- Roborazzi (record-only mode), Compose UI tests on Robolectric, ATF checks.
- `AlarmScheduler` interface + `setAlarmClock` implementation, boot/time-change receivers, notification channels from `platform.md`, `PokeActivity` (3b) and the poke notification (3a).
- Screens: welcome (0a, 0b), phone (0c), code (0d, 6c), name (0e), home empty (1a). Wire sign-in through Supabase Auth via our API flow.
- `mise run android:test` included in `test:fast` and `test`.

**Done when:** `mise run test` passes; Roborazzi images of the built screens are attached to the PR next to the design PNGs; the app installs on an emulator, signs in with a test number against the local backend (`adb reverse tcp:3000 tcp:3000`) and shows home.

## Stage 6 · E2E harness

**Read first:** `docs/testing.md` (E2E), ADR 0012.

**Tasks**
- Script to create two AVDs with Google Play images (`sdkmanager`, `avdmanager`) and boot them headless.
- `e2e` flavor: `testTagsAsResourceId`, camera replaced by file injection.
- `e2e/orchestrator` (TypeScript): starts backend in E2E mode against a separate database, installs the app on both emulators, runs Maestro flows per role, calls test endpoints, collects screenshots, logcat and server logs into `e2e-runs/<timestamp>/`.
- First smoke flow: sign up with a test number, write a task to self, full screen poke rings, tap Bitti, task leaves the list.
- `mise run e2e:smoke` runs in the background with a timeout and prints the run folder.

**Done when:** the first smoke flow passes twice in a row from a clean state.

## Stage 7 · Cloud wiring

**Read first:** `docs/architecture.md` (Environments), `docs/human-checklist.md` (Before stage 7).

**Tasks (agent prepares, human executes production steps)**
- Vercel project linked to the repo, root `apps/web`, region `fra1` and an ignored build step for Android-only changes (`apps/web/vercel.json`), env variables listed in a checked-in `.env.example`. Env is set for Production only; previews have none and serve only health.
- Domain `nuddle.app` on the Vercel project, DNS kept at Cloudflare (records in `architecture.md`, Environments).
- Supabase cloud project linked. The agent prepares the exact commands; **the human runs `supabase link` and `supabase db push`**.
- Cloud Auth settings (phone sign-in, Send SMS hook, no test numbers) as code in `supabase/cloud/`, applied by the human.
- `pg_cron` tick job enabled in the cloud project with the shared secret (Vault).
- Push: until the beta, Vercel uses an FCM-only service account of the dev Firebase project `nuddle-app-er3n`. Registering the prod app `app.nuddle` moved to "Before the beta" (decided 2026-10-11, NUD-11).
- Secrets: the human puts each value in a file under `~/.nuddle/prod/`; the agent adds them to Vercel as sensitive variables without reading them; the human writes the Supabase side (`supabase/cloud/configure.sh`).
- `/api/v1/health` returns version and DB connectivity.

**Done when:** a Vercel preview deploy answers `/api/v1/health`; after the human's `db push` and promote, production health is green and the tick job is running (visible in `task_events` or logs).

---

## After bootstrap: feature order

Build in vertical slices, each with scenario tests, UI tests and, where it touches pokes, an E2E flow:

1. Task to self end to end: compose (2a), preview (2b), undo toast, setup (2c, 2d, 2e), home with task (2f), poke (3a, 3b), done note.
2. Invite and consent: 1e, 1f, 1g, 1c, 1d, 0f, 0g.
3. Task to someone else: 4a, 4b, 4c, 4i; sender detail 7c, cancel 7d.
4. Photo and approval: 4d, 4e, 4f, 4g, 6e.
5. Drop, missed, human channel: 7f, 5d, 7e, 7a, 7b.
6. Profile and settings: 5a, 5b, 7g, 7h, 7i.
7. Missed-ring detection and brand fixes: 2g, 2h.
8. Offline and errors: 6a, 6b, 6d, 6f.
9. Voice input: 4h.
10. Landing page.
