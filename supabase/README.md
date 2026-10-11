# supabase

Local stack config (`config.toml`), migrations (Drizzle output and hand-written SQL) and `seed.sql`. See ADR 0002 and `migrations/README.md`.

- `mise run dev` starts the stack if needed, runs `supabase db reset` (migrations + seed) and starts the web app.
- Test phone numbers with fixed OTP codes live only in `config.toml` (ADR 0011). Never enable them in the cloud project.
- Never `supabase db push` or `--linked` from an agent session (CLAUDE.md hard rule 6).
- The Send SMS hook (`[auth.hook.send_sms]`) calls the web app on the host at `/api/internal/otp-hook`, signed with `SEND_SMS_HOOK_SECRET` (a local-only value in `mise.toml`). Test numbers never reach it; any other number needs `mise run dev` running and Twilio env (otherwise the hook answers 500 and sign-in fails, which is intended).
- The private `task-photos` bucket is created by a migration.

## Tick job (stage 7, human)

The `nuddle-tick` `pg_cron` job (migration `20261011143500_tick_job.sql`) calls the API every minute once two Vault secrets exist. In the cloud project's SQL editor, with the production values:

```sql
select vault.create_secret('https://<production host>/api/internal/tick', 'tick_url');
select vault.create_secret('<TICK_SECRET from Vercel>', 'tick_secret');
```

Check it with `select * from cron.job_run_details order by start_time desc limit 5;`. Without the secrets the job runs and does nothing, which is how it stays off locally.
