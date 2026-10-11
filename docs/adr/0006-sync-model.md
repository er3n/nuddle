# 0006 Sync via event cursor and idempotent commands

Status: Accepted (2026-10-11).

## Context
The app must work offline (queue changes, keep ringing), retry safely, and later support more than one device per person. Many bespoke REST endpoints with ad-hoc retry logic would be fragile, especially when agents add endpoints one by one.

## Decision
- `task_events` (and related change rows) form an ordered log. `GET /api/v1/sync?cursor=` returns everything visible to the caller since the cursor, plus the new cursor.
- Every write is a command `POST /api/v1/commands/<name>` carrying a client generated UUIDv7 `idempotencyKey`. The server stores keys and returns the original result on replay.
- Android keeps an outbox in Room, drained by WorkManager with backoff. Optimistic local state is shown with `QUEUED`.
- FCM data messages carry hints only. Sync is the source of truth.

## Consequences
- Adding a feature means: a command, its domain handler, the events it emits, and how sync exposes them. That shape is easy for agents to repeat and for scenario tests to cover.

## Rejected
- **Resource-style REST per entity with client-side reconciliation.**
- **Supabase Realtime / direct table subscriptions:** clients would bypass the API (ADR 0002).
