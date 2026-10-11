-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Deny-all safety net (ADR 0002). Android never talks to Supabase directly; only our API, which
-- connects as the table owner, reads and writes these tables. PostgREST roles get nothing.
-- test/integration/deny-all.test.ts fails when a public table misses RLS or keeps a grant.

-- Tables created later by `postgres` (our migrations) get no grants for the PostgREST roles.
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on functions from anon, authenticated, public;

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on all functions in schema public from anon, authenticated;

-- RLS with no policies: even a role that somehow gets a grant sees no rows.
alter table people enable row level security;
alter table edges enable row level security;
alter table invites enable row level security;
alter table tasks enable row level security;
alter table task_events enable row level security;
alter table poke_steps enable row level security;
alter table scheduled_actions enable row level security;
alter table devices enable row level security;
alter table idempotency_keys enable row level security;
