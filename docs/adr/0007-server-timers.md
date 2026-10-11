# 0007 Server timers: `scheduled_actions` + pure `tick(now)`

Status: Accepted (2026-10-11). Supersedes Vercel Cron and Inngest.

## Context
Server-side time rules (deadline → missed, 3 hour auto-approve, human channel, lost poke detection, missed card expiry) need minute-level precision. Inngest's free tier fills at roughly 70 users and the next tier is 99 USD. Vercel Cron at minute granularity needs Pro. Most importantly, a chain like `pg_cron → pg_net → endpoint` cannot be verified end to end by an agent, so the logic must not live in the chain.

## Decision
- Domain transitions insert rows into `scheduled_actions(kind, task_id, due_at, payload)`.
- `tick(now)` is a domain function: claim due rows (`FOR UPDATE SKIP LOCKED`), run the handler for each kind, mark done. Idempotent and safe to run concurrently.
- Production trigger: a `pg_cron` job every minute calls `POST /api/internal/tick` through `pg_net` with a shared secret. The job is defined in a SQL migration.
- Local trigger: `mise run tick` (optionally with a fake `now`). E2E trigger: `/test/tick` and `/test/advance-clock`.
- Tests call `tick(fakeNow)` directly.

## Rejected
- **Inngest:** cost curve.
- **Vercel Cron:** Pro plan needed for per-minute runs.
- **Cloudflare Worker cron / Upstash QStash:** extra vendor; not needed once Supabase provides `pg_cron`.
- **Logic in SQL (`plpgsql` handlers):** hard to test, invisible to the TS type system.
