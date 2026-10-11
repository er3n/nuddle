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

- Alarms live on the device. The server owns the task lifecycle and the poke plan (ADR 0005).
- Android talks only to our API. It never talks to PostgREST or Supabase tables directly (ADR 0002).

## Stack

| Area | Choice |
|---|---|
| Repo | Monorepo `er3n/nuddle`: pnpm workspaces for TS, Gradle for Android, mise for tool versions and tasks (ADR 0001) |
| Android | Kotlin, Jetpack Compose, single Activity + Navigation Compose, `PokeActivity` separate. Hilt, Retrofit or Ktor client with kotlinx.serialization (generated), Room, WorkManager, CameraX, kotlinx-datetime. Min SDK 26, target current (35+). |
| API | Next.js on Vercel. The API is Hono mounted in a Next route handler (`app/api/[[...route]]/route.ts`) with `@hono/zod-openapi`. Next itself serves the landing page and later the sender web page (ADR 0003). |
| Database | Supabase Postgres, Drizzle ORM. Drizzle generates SQL into `supabase/migrations/`. Hand-written SQL migrations (grants, `pg_cron` jobs, auth hooks) live in the same folder (ADR 0002). |
| Auth | Supabase Auth, phone OTP. Send SMS hook calls our endpoint, which sends via WhatsApp template or SMS. Supabase issues the JWT, the API verifies it (ADR 0011). |
| Storage | Supabase Storage, private bucket, signed upload URLs. Photos deleted when the task closes. |
| Scheduler | `scheduled_actions` table + pure `tick(now)`. In the cloud, `pg_cron` calls `POST /api/internal/tick` every minute via `pg_net`. Locally, `mise run tick` (ADR 0007). |
| Push | FCM data messages, sent with `firebase-admin`. Crashlytics and App Distribution from the same Firebase project. One project, debug build registered as `app.nuddle.debug`. |
| LLM | Behind a `TaskExtractor` interface. Provider not fixed. A gateway (e.g. Cloudflare AI Gateway) is optional. |
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
      src/app/              Next.js (landing, later sender page)
      src/api/              Hono app, routes, zod schemas
      src/domain/           pure TS: state machine, planPokes, tick, rules. No I/O imports.
      src/infra/            db (Drizzle), push, otp, llm, storage, clock
      test/                 unit, integration, scenario
  packages/
    contract/openapi.yaml   generated, committed
  supabase/
    config.toml             local stack config, test OTP numbers
    migrations/             Drizzle output + hand-written SQL
    seed.sql
  e2e/
    flows/                  Maestro YAML per device role
    orchestrator/           TS runner that drives two emulators and the test endpoints
  docs/
  .github/workflows/ci.yml
```

## Layering rules (enforced, see `quality.md`)

- `apps/web/src/domain` is pure: no imports from `infra`, `api`, Drizzle, Hono, `fetch`, or wall clock. It receives `now` and data, returns decisions.
- `apps/web/src/api` validates input with zod, calls domain, persists through `infra`, returns zod-typed output.
- Android `domain` package has no `android.*` imports. Screens are stateless composables taking `UiState` and callbacks; ViewModels are never passed into screens.
- External services sit behind interfaces with fakes: `Clock`, `PushSender`, `OtpSender`, `TaskExtractor`, `PhotoStorage` (web); `Clock`, `AlarmScheduler`, `PushReceiver` boundary (Android).

## Sync model (ADR 0006)

- **Reads:** `GET /api/v1/sync?cursor=<event id>` returns everything that changed for this person since the cursor: tasks, events, connections, invites, inbox items, plan steps for their own device. The cursor is the `task_events` (and related) sequence.
- **Writes:** commands, `POST /api/v1/commands/<name>` with a client generated UUIDv7 idempotency key. Replaying a command is safe.
- **Android:** an outbox table in Room. WorkManager drains it with backoff. Local effects are applied immediately (`QUEUED` tasks, stopped alarms).
- **Push:** FCM data messages are only hints ("sync now", "plan changed", "task cancelled"). The sync endpoint is the source of truth.

## Server timers (ADR 0007)

`scheduled_actions(id, kind, task_id, due_at, payload, done_at)` rows are created by domain transitions. `tick(now)` claims due rows with `FOR UPDATE SKIP LOCKED`, runs the matching domain handler, and is idempotent. Kinds: `deadline`, `approval_timeout`, `human_check`, `lost_poke_check`, `missed_expiry`, `permission_request_cooldown` **(proposed)**.

## E2E mode

`E2E=1` on the web app turns on: fixture `TaskExtractor`, fixed test OTP codes, compressed poke plans (seconds instead of minutes), and the test endpoints `/test/reset`, `/test/seed`, `/test/tick`, `/test/advance-clock`. The code for these lives in a module that is not imported in production builds, and a test asserts the routes return 404 when `E2E` is not set. Details in `testing.md`.

## Environments

| Env | Web | DB | Firebase app |
|---|---|---|---|
| local | `next dev` on the host | `supabase start` (Docker) | `app.nuddle.debug` |
| e2e | `next dev` with `E2E=1` | same local stack, separate database | `app.nuddle.debug`, flavor `e2e` |
| preview | Vercel preview per PR | none (health and contract only) | none |
| prod (= beta) | Vercel production | Supabase cloud project | `app.nuddle` |

A staging project is added before the global launch, not before.

## Costs (rough, to confirm)

Beta: Supabase free, Vercel hobby, Firebase free, LLM usage small. Launch: Supabase Pro ~25 USD + Vercel Pro ~20 USD per month plus usage. Note that Supabase free projects pause after a week of inactivity; the per-minute `pg_cron` job keeps it active.
