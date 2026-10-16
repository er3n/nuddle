# Architecture

Decided on 2026-10-11. Each decision has an ADR in `docs/adr/` with the reasoning and the rejected alternatives. Read the ADR before proposing a change.

Context that shapes everything: **one-person team, code written mostly by agents.** The bottleneck is review and keeping the system running, not typing. Prefer managed services, everything as code, fast deterministic tests and machine-enforced rules.

## Overview

```
Android app (Kotlin, Compose)                       Vercel
  Room: tasks, poke steps, outbox      REST /api/v1   apps/web: Next.js (landing) + Hono (API)
  AlarmManager.setAlarmClock    <----------------->   domain/ (pure TS)  tick(now)
  WorkManager: outbox, uploads    generated client    infra/: db, push, otp, llm, storage
  FCM receiver                  <---- FCM data ----   firebase-admin
                                                              |
                                                       Supabase (eu-central-1)
                                                         Postgres (+ pg_cron trigger)
                                                         Auth (phone OTP, Send SMS hook)
                                                         Storage (private photo bucket)
```

- Alarms live on the device. The server owns the task lifecycle and the poke plan (ADR 0005); an LLM proposes the plan and the server checks it against the rules (ADR 0017).
- Android talks only to our API. It never talks to PostgREST or Supabase tables directly (ADR 0002).

## Stack

| Area | Choice |
|---|---|
| Repo | Monorepo `er3n/nuddle`: pnpm workspaces for TS, Gradle for Android, mise for tool versions and tasks (ADR 0001) |
| Android | Kotlin, Jetpack Compose, single Activity + Navigation Compose, `PokeActivity` separate. Hilt, Retrofit client with kotlinx.serialization (generated), Room, WorkManager, CameraX, kotlinx-datetime. Min SDK 26, target current (35+). |
| API | Next.js on Vercel. The API is Hono mounted in a Next route handler (`app/api/[[...route]]/route.ts`) with `@hono/zod-openapi`. Next itself serves the landing page and later the sender web page (ADR 0003). |
| Database | Supabase Postgres, Drizzle ORM. Drizzle generates SQL into `supabase/migrations/`. Hand-written SQL migrations (grants, `pg_cron` jobs, auth hooks) live in the same folder (ADR 0002). |
| Auth | Supabase Auth, phone OTP, reached through our API (`/api/v1/auth/*`). Send SMS hook calls our endpoint, which sends via SMS (Twilio) and later a WhatsApp template. Supabase issues the JWT, the API verifies it against Supabase's JWKS; the person id is the JWT `sub` (ADR 0011, 0014). |
| Storage | Supabase Storage, private bucket, signed upload URLs. Photos deleted when the task closes, and again by a `photo_cleanup` timer 15 minutes later that keeps retrying until Storage answers (NUD-25). |
| Scheduler | `scheduled_actions` table + pure `tick(now)`. In the cloud, `pg_cron` calls `POST /api/internal/tick` every minute via `pg_net`. Locally, `mise run tick` (ADR 0007). |
| Push | FCM data messages, sent with `firebase-admin`. Crashlytics (crashes, plus breadcrumbs and non-fatal reports without personal data, ADR 0023) and App Distribution from the same Firebase project. One project, debug build registered as `app.nuddle.debug`. |
| LLM | Behind two ports: `TaskExtractor` (text to task) and `PokePlanner` (task and history to poke plan, run after the response, ADR 0017). Default provider: OpenRouter (OpenAI-compatible API, one key, model switched by config). Env: `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`. Model: `deepseek/deepseek-v4.1-flash` (decided 2026-10-11, no model comparison in the MVP). |
| Contract | zod schemas in `apps/web` → `packages/contract/openapi.yaml` (committed) → Kotlin client generated at build time (ADR 0008). |
| Region | Vercel functions `fra1`, Supabase `eu-central-1`. KVKK: cross-border transfer must be named in the privacy notice. |

Rejected and replaced on 2026-10-11 (do not reintroduce without a new ADR): Neon, Better Auth, Cloudflare R2, Vercel Cron, Inngest, separate `android` and `web` repos, Cloudflare Workers as the API runtime, a self-hosted Kotlin/Ktor backend.

## Repository layout

```
nuddle/
  CLAUDE.md
  mise.toml                 tool versions + tasks (dev, fix, lint, test:fast, test, e2e:smoke, tick, eval)
  lefthook.yml
  apps/
    android/                Gradle project, applicationId app.nuddle, flavors: dev, e2e, prod
    web/
      src/app/              Next.js routes: landing `/` (en) and `/tr`, invite link page, later sender page
      src/landing/          landing, invite and 404 components; colors from docs/ux/tokens.json
      src/i18n/             web locales and dictionaries (tr is the source, en must match it)
      src/api/              Hono app, routes, zod schemas
      src/domain/           pure TS: state machine, planPokes (v0), checkPlan, tick, rules. No I/O imports.
      src/infra/            db (Drizzle), push, otp, llm, storage, clock
      test/                 unit, integration, scenario
  packages/
    contract/openapi.yaml   generated, committed
  supabase/
    config.toml             local stack config, test OTP numbers
    migrations/             Drizzle output + hand-written SQL
    seed.sql
  e2e/
    orchestrator/           TS runner that drives two emulators over adb and the test endpoints (ADR 0018)
  docs/
  .github/workflows/ci.yml
```

## Layering rules (enforced, see `quality.md`)

- `apps/web/src/domain` is pure: no imports from `infra`, `api`, Drizzle, Hono, `fetch`, or wall clock. It receives `now` and data, returns decisions.
- `apps/web/src/api` validates input with zod, calls domain, persists through `infra`, returns zod-typed output.
- Android `domain` package has no `android.*` imports. Screens are stateless composables taking `UiState` and callbacks; ViewModels are never passed into screens.
- External services sit behind interfaces with fakes: `Clock`, `PushSender`, `OtpSender`, `TaskExtractor`, `PhotoStorage` (web); `Clock`, `AlarmScheduler`, `Diagnostics`, `PushReceiver` boundary (Android).

## Sync model (ADR 0006)

- **Reads:** `GET /api/v1/sync?cursor=<n>` returns everything that changed for this person since the cursor: tasks, events, connections (with reachability), sent and received invites, and plan steps for tasks they receive. The cursor is the shared `sync_seq` sequence behind the `change_seq` column of `tasks`, `task_events`, `edges` and `invites`; writers serialize on one advisory lock so the cursor never skips a late commit (ADR 0014). Omit the cursor for a full sync.
- **Hidden plan in sync:** plan steps and `plan_generated` events go only to the recipient; `HUMAN` steps never leave the server. A declined invite does not move the inviter's cursor.
- **Writes:** commands, `POST /api/v1/commands/<name>` with a client generated UUIDv7 idempotency key. Replaying a command is safe.
- **Android:** an outbox table in Room. WorkManager drains it with backoff; a new command starts a fresh drain instead of waiting behind one in backoff, and a sync follows every drain. Local effects are applied immediately (`QUEUED` tasks, stopped alarms), and a sync that lands before they are sent (or before a photo is uploaded) keeps them: the task's status, whether it is on the recipient's list and its snooze stay as the phone set them until the server has the command (NUD-64).
- **Push:** FCM data messages are only hints ("sync now", "plan changed", "task cancelled"). The sync endpoint is the source of truth. Every task event either person can see sends a hint to both people (NUD-59; the plan and rings stay with the recipient), so both lists follow within seconds; a hint never shows anything by itself, notifications come from what sync brings. The phone syncs right inside the push (high priority keeps it awake), and again whenever the app comes to the front; syncs run one at a time.

## Server timers (ADR 0007)

`scheduled_actions(id, kind, task_id, due_at, payload, done_at, claimed_until)` rows are created by domain transitions. `tick(now)` claims due rows with `FOR UPDATE SKIP LOCKED` and a one minute lease (`claimed_until`), runs the matching domain handler, and completes each row in its own transaction; a row a task transition already finished is skipped. Idempotent and safe to run concurrently. The `pg_cron` job (`nuddle-tick`, every minute) reads the URL and secret from Supabase Vault (`tick_url`, `tick_secret`) and does nothing without them, which keeps it off locally. Kinds: `deadline`, `approval_timeout`, `human_check`, `lost_poke_check`, `missed_expiry`, `permission_request_cooldown`, `photo_cleanup` **(proposed)**, `account_purge` (no task, `payload.personId`; deletes what is left of a deleted account a week later, `domain.md` Account deletion). A `photo_cleanup` that Storage does not answer is not completed but moved 15 minutes later; it does nothing for a task that is open or waits for approval again (a late photo, 6e).

## E2E mode

`E2E=1` on the web app turns on: fixture `TaskExtractor` (`e2e/fixtures/extractions.json`), no `PokePlanner` (v0 plans only), fixed test OTP codes, compressed poke plans (step offsets divided by 60: minutes become seconds; deadlines unchanged), and the test endpoints `/api/test/reset`, `/api/test/seed`, `/api/test/tick`, `/api/test/advance-clock` (under `/api` because Hono is mounted there). The code lives in `src/api/e2e.ts`, loaded only with a dynamic import when `E2E=1`, and a test asserts the routes return 404 when `E2E` is not set. Details in `testing.md`.

## API

- Public routes under `/api/v1` are defined with `@hono/zod-openapi` (ADR 0003, 0008): `auth/otp`, `auth/verify` (Turkish mobile numbers only until the global launch, open-questions #45), `auth/refresh`, `health` (no token), `sync`, `devices`, `photos/upload-url`, `photos/view-url` (a short-lived signed URL to the photo awaiting approval, for the two people on the task), `avatar/upload-url` and `avatar/view-url` (the profile picture: mine or an active connection's, in the same private bucket under `avatars/<person>/`; NUD-72), and one `commands/<name>` per action in `domain.md`. Every command body carries a UUIDv7 `idempotencyKey`; a replay returns the stored result, a failed command stores nothing.
- Internal routes: `POST /api/internal/tick` (bearer `TICK_SECRET`), `POST /api/internal/delete-account` (bearer `TICK_SECRET`; `{ phone, confirm }`, a dry run without `confirm`; the in-app deletion for an email request, `mise run account:delete`, NUD-88), `POST /api/internal/otp-hook` (Supabase Send SMS hook, Standard Webhooks signature with `SEND_SMS_HOOK_SECRET`). Not in the contract.
- Every response carries `X-Request-Id`. Errors have the shape `{ "error": { "code", "name", "message", "requestId" } }`.

### Error codes

The app shows the code on the unrecoverable error screen (6f) and sends it to Crashlytics. Codes never change meaning; add new ones at the end of a range.

| Code | Name | HTTP | Meaning |
|---|---|---|---|
| DRT-1001 | `validation_failed` | 400 | Body or query does not match the schema |
| DRT-1002 | `unauthorized` | 401 | Missing, expired or foreign access token; bad internal secret |
| DRT-1003 | `not_registered` | 403 | Signed in, but `register` was not sent yet (or the account is deleted) |
| DRT-1004 | `not_found` | 404 | No such route, or a task, invite the caller cannot see |
| DRT-1005 | `idempotency_key_reused` | 422 | The key was used for a different command |
| DRT-1006 | `invalid_otp` | 401 | Wrong or expired code (`details.remainingAttempts`), or an unknown refresh token |
| DRT-1007 | `forbidden` | 403 | Reserved |
| DRT-1008 | `otp_locked` | 429 | Too many wrong codes for the phone: no code is checked or sent until `details.retryAt` (screen 6c) |
| DRT-1009 | `otp_limit` | 429 | Too many codes sent to the phone, or to everyone, in the last 24 hours: none is sent until `details.retryAt` (ADR 0024) |
| DRT-1010 | `write_limit` | 429 | The caller wrote too many texts in the last 24 hours: nothing is extracted until `details.retryAt` (ADR 0024) |
| DRT-4001 | `wrong_status` | 409 | The task's status does not allow the command |
| DRT-4002 | `not_sender` | 403 | Only the sender can do this |
| DRT-4003 | `not_recipient` | 403 | Only the recipient can do this |
| DRT-4004 | `wrong_finish_mode` | 409 | Done on a photo task (without camera fallback) or a photo on a done task |
| DRT-4005 | `deadline_not_in_future` | 409 | |
| DRT-4006 | `not_connected` | 409 | No active connection to the recipient |
| DRT-4007 | `recipient_unavailable` | 409 | Sender or recipient account deleted |
| DRT-4008 | `snooze_not_allowed` | 409 | No snooze left, or it would run past the deadline |
| DRT-4009 | `start_not_in_window` | 409 | The recipient's start is in the past, after the task's time, or more than the lead limit before it (NUD-78) |
| DRT-4101 | `invite_not_open` | 409 | |
| DRT-4102 | `wrong_invitee` | 403 | The invite is bound to another number |
| DRT-4103 | `self_invite` | 409 | |
| DRT-4104 | `not_inviter` | 403 | Only the inviter can revoke an invite |
| DRT-5001 | `internal` | 500 | Unexpected server error |
| DRT-5002 | `upstream_failed` | 502 | Supabase Auth, the SMS provider or the LLM failed |

## Environments

| Env | Web | DB | Firebase app |
|---|---|---|---|
| local | `next dev` on the host | `supabase start` (Docker) | `app.nuddle.debug` |
| e2e | `next dev` with `E2E=1` | same local stack, separate database | `app.nuddle.debug`, flavor `e2e` |
| preview | none: ticket branches (`nud-*`) are not deployed (Hobby: 100 deployments a day per account) | none | none |
| prod (= beta) | Vercel production | Supabase cloud project | `app.nuddle` (registered before the beta; until then the dev project) |

A staging project is added before the global launch, not before.

Production wiring (stage 7):
- Vercel project `nuddle` (team `er3ns-projects`), root `apps/web`, functions in `fra1`. `apps/web/vercel.json` skips builds whose changes are all under `apps/android/`.
- Host `https://nuddle.app`. DNS stays at Cloudflare with "DNS only" (not proxied) records, the values Vercel recommended for this project on 2026-10-11: `A nuddle.app 216.198.79.1`, `A nuddle.app 64.29.17.1` and `CNAME www 025ac255c6824333.vercel-dns-017.com`. `www` redirects to the apex (308).
- Vercel env is set for the Production target only (`.env.example`). `apps/web/vercel.json` turns off deployments of ticket branches (`nud-*`); only `main` deploys. A deployment without env (a manual preview) serves only `/api/v1/health` (database `not_configured`). `*.vercel.app` URLs sit behind Vercel Authentication. `E2E` is never set in Vercel.
- The API reaches Postgres through Supabase's transaction pooler (port 6543): the direct host is IPv6 only.
- Merging to `main` deploys production.
- Auth settings of the cloud project (phone sign-in, Send SMS hook, no test numbers) are code in `supabase/cloud/`, applied through the Management API, never with `supabase config push`.
- Until the beta, push uses an FCM-only service account of the dev Firebase project `nuddle-app-er3n`.

## Costs (rough, to confirm)

Beta: Supabase free, Vercel hobby, Firebase free, LLM usage small. Launch: Supabase Pro ~25 USD + Vercel Pro ~20 USD per month plus usage. Note that Supabase free projects pause after a week of inactivity; the per-minute `pg_cron` job keeps it active.

Per task: two LLM calls (extraction and plan), each kept in `llm_calls` with its tokens and what OpenRouter charged (`cost_usd`, ADR 0024); the cost of a task is the sum over its rows, and `mise run golden:export` carries them. Per sign-in: one SMS, 0.0305 USD to Turkey (open-questions #45).

### Cost guardrails (ADR 0024)

What one person, a bug or an attacker can spend is bounded twice:

- **In code**, counted over the last 24 hours (`apps/web/src/domain/cost-caps.ts`, open-questions #54): extractions per writer (`write_limit` over the cap), plans and profiles per person (plan v0 over the cap), sign-in codes per phone and in total (the Send SMS hook sends nothing over the cap; `otp_limit` from `/v1/auth/otp`). Supabase Auth's own SMS limits (per hour for the project, seconds between codes to one phone) are in `supabase/cloud/auth.json`.
- **Outside the code**, set by the human (`human-checklist.md`, Cost limits): a prepaid OpenRouter credit with a limit on the key and no auto top-up; Twilio SMS Geo permissions to Turkey only, no auto-recharge and a low balance alert; Vercel spend management with a cap that pauses the project. These hold even if the code is wrong.
