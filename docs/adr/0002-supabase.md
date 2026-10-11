# 0002 Supabase for Postgres, Auth and Storage

Status: Accepted (2026-10-11). Supersedes Neon + Better Auth + Cloudflare R2.

## Context
The first stack (Neon, Better Auth, R2, Vercel Cron) had three problems:
1. A per-minute cron needs Vercel Pro, and per-minute DB polling keeps Neon awake, eating free compute.
2. Better Auth is cookie/session centric; a phone-only native client needs the bearer plugin and we would write the OTP sending anyway.
3. Four vendors for one person to operate.

## Decision
- **Supabase** provides Postgres, Auth (phone OTP with a Send SMS hook) and Storage (private bucket, signed uploads), region `eu-central-1`.
- `pg_cron` + `pg_net` trigger our tick endpoint every minute (ADR 0007).
- Managed only through the CLI and `supabase/migrations/`. Nothing is configured by clicking in the dashboard. If it is not in the repo, it does not exist.
- **Android never talks to Supabase directly** (no PostgREST, no `supabase-kt` data access). All reads and writes go through our API, which keeps the sync model and plan ownership in one place.
- Because of that, Row Level Security is a single deny-all safety net: revoke table access from `anon` and `authenticated`. No per-table policies to design or test.
- Local development uses `supabase start` (Docker). Unused services (Studio, Realtime, analytics, edge runtime) are disabled in `config.toml`.

## Consequences
- Drizzle remains the ORM and schema source; `drizzle-kit generate` writes into `supabase/migrations/`.
- Test phone numbers with fixed OTP codes are configured in `config.toml` for local and E2E.
- Free projects pause after a week of inactivity. The per-minute cron keeps the project active.
- Launch cost roughly 25 USD/month for Supabase Pro (to confirm).

## Rejected
- **Neon + Better Auth + R2 + Vercel Cron:** see context.
- **Cloudflare Workers + D1/Hyperdrive:** cheapest, but loses Postgres (D1) or keeps the Neon problem, `firebase-admin` does not run on Workers, auth would be hand written.
- **Firebase (Firestore, Auth, Functions, Cloud Tasks):** NoSQL is a poor fit for the graph, phone auth cannot use our WhatsApp sender, heaviest lock-in.
- **Ktor/Spring Boot on a VPS:** best type sharing and in-process scheduler, but a one-person team should not operate servers; agent fluency is also lower.
- **Convex, Appwrite, PocketBase, AWS serverless, Django:** see the 2026-10-11 discussion; each loses on either Kotlin client support, graph fit, or setup weight.
- **Self-hosting Supabase in Docker in production:** brings back the ops burden with a more complex stack.
