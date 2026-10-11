-- Local seed: the Selin and Eren cast from docs/glossary.md, connected as spouses, and the sample
-- task "Temizlikçi için para çek · 1.000 TL · yarın 09:00 · fotoğrafla". Their phones are test
-- numbers with fixed OTP codes in config.toml. Applied by `supabase db reset`.

insert into people (id, phone, display_name) values
  ('00000000-0000-4000-8000-000000000001', '+905320000001', 'Selin'),
  ('00000000-0000-4000-8000-000000000002', '+905320000002', 'Eren');

-- One 1:1 connection is two directed edges created together (ADR 0010).
insert into edges (from_person_id, to_person_id, type, status) values
  ('00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002', 'spouse', 'active'),
  ('00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001', 'spouse', 'active');

insert into tasks (id, sender_id, recipient_id, title, detail, deadline, finish_mode, raw_text) values (
  '00000000-0000-4000-8000-000000000101',
  '00000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000002',
  'Temizlikçi için para çek',
  '1.000 TL',
  -- Tomorrow 09:00 in Istanbul, relative to when the seed runs.
  ((now() at time zone 'Europe/Istanbul')::date + 1 + time '09:00') at time zone 'Europe/Istanbul',
  'photo',
  'eren yarın 9dan önce temizlikçi için 1000 çeksin'
);

insert into task_events (task_id, actor_id, type) values
  ('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001', 'created');
