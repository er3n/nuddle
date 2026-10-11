-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Server timers trigger (ADR 0007): pg_cron calls POST /api/internal/tick every minute through
-- pg_net. The URL and the shared secret come from Supabase Vault (`tick_url`, `tick_secret`);
-- without them the job does nothing, which keeps it off locally. The human adds both secrets in
-- the cloud project in stage 7 (supabase/README.md, Tick job).

create schema if not exists private;
revoke all on schema private from public;

create or replace function private.call_tick() returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  tick_url text;
  tick_secret text;
begin
  select decrypted_secret into tick_url from vault.decrypted_secrets where name = 'tick_url';
  select decrypted_secret into tick_secret from vault.decrypted_secrets where name = 'tick_secret';
  if tick_url is null or tick_secret is null then
    return;
  end if;
  perform net.http_post(
    url := tick_url,
    headers := jsonb_build_object(
      'Authorization', 'Bearer ' || tick_secret,
      'Content-Type', 'application/json'
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 55000
  );
end
$$;

revoke all on function private.call_tick() from public;

-- pg_cron only runs in the `postgres` database; the per-run test databases skip the schedule.
do $$
begin
  if current_database() = 'postgres'
    and exists (select 1 from pg_available_extensions where name = 'pg_cron')
    and exists (select 1 from pg_available_extensions where name = 'pg_net')
  then
    create extension if not exists pg_cron with schema pg_catalog;
    create extension if not exists pg_net with schema extensions;
    perform cron.schedule('nuddle-tick', '* * * * *', 'select private.call_tick()');
  end if;
end
$$;
