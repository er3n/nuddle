# Human checklist

Things only the human can do: accounts, payments, logins, secrets, production pushes and real-device checks. Everything else is the agent's job.

## Before stage 1 (about an hour)

- [ ] Create the public repo `er3n/nuddle` on the personal account. Commit this docs pack as the first commit.
- [ ] In the repo settings: enable secret scanning push protection, CodeQL default setup and Dependabot alerts; keep "require approval for workflows from fork PRs".
- [ ] Install on the dev machine: Docker (Supabase local needs it), Android Studio with the Android SDK, mise, Claude Code.
- [ ] Machine supports hardware virtualization for emulators (KVM on Linux, Hypervisor Framework on macOS).
- [ ] `gh auth login`.
- [ ] Linear: workspace **Nuddle** exists. Install the Linear GitHub integration for `er3n/nuddle` (links branches and PRs, closes issues on merge).
- [ ] Add the Linear MCP server to Claude Code on this machine and authorize it for the Nuddle workspace: `claude mcp add --transport http linear https://mcp.linear.app/mcp`, then run `/mcp` inside Claude Code to log in. (The claude.ai connector does not carry over to Claude Code.)
- [ ] Create an OpenRouter API key (default LLM provider, open-questions #16; needed by stage 3 for `mise run eval`). Put `OPENROUTER_API_KEY=...` and `OPENROUTER_MODEL=deepseek/deepseek-v4.1-flash` in `apps/web/.env.local`, never in the repo. Set a monthly credit limit on the key.

## Before stage 5

- [ ] Create a Firebase project. Register the Android app `app.nuddle.debug` and download `google-services.json` for it (or let the agent use `firebase` CLI after `firebase login`).
- [ ] `firebase login`.

## Before stage 6

- [ ] At least 16 GB RAM free for two emulators plus Docker, or accept running one emulator at a time.

## Before stage 7

- [ ] Buy `nuddle.app` (first year about 9.99 USD, then about 15 USD/year). Optional: `durt.app`.
- [ ] Supabase account, create the cloud project in `eu-central-1` (Frankfurt). `supabase login`.
- [ ] Vercel account linked to GitHub. `vercel login`.
- [ ] Register `app.nuddle` (prod) in Firebase. Create a service account key for `firebase-admin` and put it in Vercel env.
- [ ] Generate the shared secrets (tick endpoint, OTP hook) and put them in Vercel and Supabase.
- [ ] Run `supabase db push` yourself when the agent says the migrations are ready. Promote the Vercel deployment yourself.

## Before the beta (about 20 families)

- [ ] Google Play Console account. Declare the app as an alarm app (`USE_EXACT_ALARM`, full screen intent). Upload an early closed test build to learn the policy outcome.
- [ ] SMS provider account (WhatsApp Business authentication template later). Configure sender, add credentials to env.
- [ ] Privacy notice and terms (KVKK): name the cross-border transfer (Supabase Frankfurt, Vercel, Firebase), photo retention (deleted when the task closes), account deletion.
- [ ] Physical test phones: at least one Xiaomi and one Samsung, plus your own.
- [ ] Run the manual checklist in `testing.md` on each beta build.

## Before the global launch

- [ ] Trademark check for Nuddle (US/EU) and the status of `nuddle.com`.
- [ ] English strings, English landing page.
- [ ] Switch Vercel and Supabase to paid plans; add a staging Supabase project.
