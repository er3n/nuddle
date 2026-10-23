# supabase

Local stack config (`config.toml`), migrations (Drizzle output and hand-written SQL) and `seed.sql`. See ADR 0002 and `migrations/README.md`.

- `mise run dev` starts the stack if needed, runs `supabase db reset` (migrations + seed) and starts the web app.
- Test phone numbers with fixed OTP codes live only in `config.toml` (ADR 0011). Never enable them in the cloud project.
- Never a bare `supabase db push` or `--linked` from an agent session. Until the beta the agent applies merged migrations with `configure.sh migrate` (CLAUDE.md hard rule 6, Cloud project below).
- The Send SMS hook (`[auth.hook.send_sms]`) calls the web app on the host at `/api/internal/otp-hook`, signed with `SEND_SMS_HOOK_SECRET` (a local-only value in `mise.toml`). Test numbers never reach it; any other number needs `mise run dev` running and Twilio env (otherwise the hook answers 500 and sign-in fails, which is intended).
- The private `task-photos` bucket is created by a migration.

## Cloud project (stage 7, human)

Project `nuddle`, ref `hksqgdkbxalaixjghlvi`, `eu-central-1`. Agents run these commands only when the human asks (CLAUDE.md hard rule 6), from the repo root. The supabase CLI is pinned in `mise.toml`, hence `mise exec --`. Secrets come from `~/.nuddle/prod/` (`docs/human-checklist.md`, Before stage 7).

1. Link (asks for the database password): `mise exec -- supabase link --project-ref hksqgdkbxalaixjghlvi`
2. Check what is applied (read-only): `mise exec -- supabase migration list --linked`
3. Apply the migrations: `mise exec -- supabase db push --linked --dry-run`, then without `--dry-run`. **Never `--include-seed`**: `seed.sql` is local test data.
4. Shared secrets and Auth settings: `mise exec -- supabase/cloud/configure.sh secrets`, then `mise exec -- supabase/cloud/configure.sh auth`. The second applies `cloud/auth.json` (phone sign-in, Send SMS hook to `https://nuddle.app/api/internal/otp-hook`, no test numbers except the Play review number from `~/.nuddle/prod/PLAY_REVIEW_OTP` when that file exists, ADR 0022) through the Management API. **Never `supabase config push` to the cloud project**: it would push `config.toml`, including the test numbers (ADR 0011).
5. After production health is green, the tick job (below): `mise exec -- supabase/cloud/configure.sh tick`

### New migrations (until the beta: the agent)

Right after the PR that adds a migration is merged: `mise exec -- supabase/cloud/configure.sh migrate --dry-run`, then without `--dry-run`. It runs `supabase db push` against the session pooler (port 5432, built from `~/.nuddle/prod/DATABASE_URL`; the API itself uses the transaction pooler), never with the seed. Before the beta the agent may delete cloud data when a migration cannot handle existing rows. `supabase db reset` against the cloud stays with the human: it rebuilds more than the app tables. From the beta on, the human runs `migrate` again.

## Tick job

The `nuddle-tick` `pg_cron` job (migration `20261011143500_tick_job.sql`) calls the API every minute once two Vault secrets exist: `tick_url` (`https://nuddle.app/api/internal/tick`) and `tick_secret` (the `TICK_SECRET` Vercel has). `configure.sh tick` stores both, or updates them when they exist. Without them the job runs and does nothing, which is how it stays off locally.

Check it:

```sh
mise exec -- supabase db query --linked "select status, return_message, start_time from cron.job_run_details order by start_time desc limit 5"
mise exec -- supabase db query --linked "select status_code, created from net._http_response order by created desc limit 5"
```

`cron.job_run_details` shows the job ran; `net._http_response` shows the API's answer (200 with `{"handled":n}`).
