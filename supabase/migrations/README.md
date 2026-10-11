# supabase/migrations

Drizzle-generated SQL plus hand-written migrations (grants, `pg_cron` jobs, auth hooks). Everything is code: no dashboard-only configuration (ADR 0002).

- Change `apps/web/src/infra/db/schema.ts`, then `pnpm --filter @nuddle/web db:generate --name <slug>`. drizzle-kit writes `<timestamp>_<slug>.sql` here and its snapshot into `meta/`.
- Hand-written migrations use the same `<timestamp>_<slug>.sql` naming and must sort after the migration they depend on.
- Every file starts with `set local lock_timeout` and `set local statement_timeout`; add them to generated files before committing. `squawk` (in `mise run lint`) enforces this and the other safe-migration rules (`.squawk.toml`).
- Every new public table needs `enable row level security` and no grants to `anon` or `authenticated`; `apps/web/test/integration/deny-all.test.ts` fails otherwise.
- Apply locally with `supabase db reset` (also runs `../seed.sql`).
