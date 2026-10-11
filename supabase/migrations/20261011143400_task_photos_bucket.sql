-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Private photo bucket (docs/architecture.md, Storage). The API hands out signed upload URLs and
-- deletes a task's photos when the task closes; nobody else reads or writes it. Skipped where
-- Supabase Storage is absent (the per-run test databases).
do $$
begin
  if to_regclass('storage.buckets') is not null then
    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values ('task-photos', 'task-photos', false, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
    on conflict (id) do nothing;
  end if;
end
$$;
