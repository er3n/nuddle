# src/infra

Adapters behind interfaces (`docs/architecture.md`, Layering rules): `db` (Drizzle schema, connection, `store.ts` queries), `auth` (JWT verification, Supabase Auth gateway), `push` (FCM, log), `otp` (Twilio), `storage` (Supabase Storage photos), `llm` (OpenRouter, fixtures), `clock.ts` (the only wall clock, plus the E2E offset clock).
