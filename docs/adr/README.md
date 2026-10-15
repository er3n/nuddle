# Architecture decision records

One file per decision: context, decision, consequences, rejected alternatives. Status is `Accepted` unless noted.

To change a decision: add a new ADR that supersedes the old one, set the old one's status to `Superseded by NNNN`, then update `architecture.md`, then change code. Never edit an accepted ADR's decision in place.

| # | Decision |
|---|---|
| 0001 | Monorepo: pnpm workspaces + Gradle + mise |
| 0002 | Supabase for Postgres, Auth and Storage; Android talks only to our API |
| 0003 | Next.js + Hono on Vercel for API and landing |
| 0004 | Native Android first (Kotlin + Compose), iOS later in Swift |
| 0005 | Poke plan generated on the server, executed on the device with `setAlarmClock` |
| 0006 | Sync via event cursor and idempotent commands |
| 0007 | Server timers: `scheduled_actions` + pure `tick(now)`, triggered by `pg_cron` |
| 0008 | Contract: zod → OpenAPI → generated Kotlin client |
| 0009 | Time is always injected |
| 0010 | Graph of 1:1 connections, no groups or roles |
| 0011 | Phone number is the only identity |
| 0012 | Testing strategy with E2E mode and Maestro (driver superseded by 0018) |
| 0013 | No alarms in the system Clock app |
| 0014 | API sign-in proxy, person id = JWT `sub`, one sync sequence, Twilio for SMS |
| 0015 | Push reaches a device by Firebase Installation ID, not registration token |
| 0016 | Public invite preview before sign-in |
| 0017 | The LLM plans the pokes from the task and the recipient's history; the server enforces the rules, v0 is the fallback |
| 0018 | E2E driven over adb on fake time, without Maestro |
| 0019 | Every LLM call is kept with its input, answer and outcome |
| 0020 | The planner reads a rolling person profile plus a short recent history |
| 0021 | A repeating task is a series of one-off occurrences |
