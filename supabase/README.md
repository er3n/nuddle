# supabase

Local stack config (`config.toml`), migrations (Drizzle output and hand-written SQL) and `seed.sql`. See ADR 0002 and `migrations/README.md`.

- `mise run dev` starts the stack if needed, runs `supabase db reset` (migrations + seed) and starts the web app.
- Test phone numbers with fixed OTP codes live only in `config.toml` (ADR 0011). Never enable them in the cloud project.
- Never `supabase db push` or `--linked` from an agent session (CLAUDE.md hard rule 6).
