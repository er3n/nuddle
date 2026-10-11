-- Local seed: the Selin and Eren cast from docs/glossary.md, connected as spouses, and the sample
-- task "Temizlikçi için para çek · 1.000 TL · yarın 09:00 · fotoğrafla". Their phones are test
-- numbers with fixed OTP codes in config.toml. Applied by `supabase db reset`.

-- A person's id is their Supabase Auth user id (the JWT `sub`), so the seeded people are also
-- auth users. Signing in with a test number then yields the seeded person. Token columns are ''
-- because Supabase Auth cannot read NULL there.
insert into auth.users (
  instance_id, id, aud, role, phone, phone_confirmed_at, raw_app_meta_data, raw_user_meta_data,
  created_at, updated_at, confirmation_token, recovery_token, email_change_token_new, email_change
)
select '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated', u.phone,
  now(), '{"provider": "phone", "providers": ["phone"]}', '{}', now(), now(), '', '', '', ''
from (values
  ('00000000-0000-4000-8000-000000000001'::uuid, '905320000001'),
  ('00000000-0000-4000-8000-000000000002'::uuid, '905320000002')
) as u (id, phone);

insert into auth.identities (provider_id, user_id, identity_data, provider, created_at, updated_at)
select u.id::text, u.id, jsonb_build_object('sub', u.id::text, 'phone', u.phone, 'phone_verified', true),
  'phone', now(), now()
from auth.users u
where u.id in ('00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002');

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
