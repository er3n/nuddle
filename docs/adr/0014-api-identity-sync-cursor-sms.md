# 0014 API sign-in proxy, person id, sync cursor and SMS provider

Status: Accepted (2026-10-11). Refines ADRs 0002, 0006 and 0011; decided by the human in NUD-8 (stage 4).

## Context
Stage 4 wires the domain to HTTP and Postgres. Four details were open in the docs: how the app signs in without talking to Supabase directly, how a Supabase Auth user maps to a `people` row, what the sync cursor covers besides `task_events`, and which provider sends the OTP.

## Decision
- **Sign-in through our API.** `POST /api/v1/auth/otp`, `/auth/verify` and `/auth/refresh` call Supabase Auth on the app's behalf. The app talks only to our API (ADR 0002), and our API owns the attempt limits of screen 6c.
- **Person id = Supabase Auth user id.** `people.id` equals the JWT `sub`. The `register` command (name screen 0e) creates the row. There is no foreign key to `auth.users`, so test databases need no `auth` schema. The local seed creates matching auth users.
- **One sync sequence.** A Postgres sequence `sync_seq` feeds a `change_seq` column on `tasks`, `task_events`, `edges` and `invites`; every insert and update takes the next value. `GET /api/v1/sync?cursor=` returns rows with a larger value. Writers serialize on one advisory lock so values commit in order and a cursor never skips a late commit. Changes that a reader must see on another row bump that row (a device change bumps the edges pointing at its owner, a missed card expiry bumps its task).
- **SMS provider: Twilio Programmable Messaging**, called from the Send SMS hook endpoint. WhatsApp templates can later use the same vendor.

## Consequences
- One writer at a time is fine for the beta; per-person locks or a commit-ordered log replace the global lock when writes contend.
- The plan stays hidden: sync returns plan steps only for tasks the caller receives, and `plan_generated` events only to the recipient.
- A declined invite does not move the inviter's cursor, so the inviter's feed cannot reveal it.

## Rejected
- **Android signs in with supabase-kt directly:** a second client and a second error surface; attempt limits would live in Supabase only.
- **A separate `people.auth_user_id` column:** one more id to carry around for no benefit.
- **One cursor per table in an opaque string, or a snapshot of connections and invites on every sync:** more client logic or more bytes for the same result.
- **Netgsm** (cheapest in Turkey, no WhatsApp, Turkey only) and **Infobip** (sales-led onboarding) as SMS providers.
