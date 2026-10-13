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

- [x] Create a Firebase project. Register the Android app `app.nuddle.debug` and download `google-services.json` for it (or let the agent use `firebase` CLI after `firebase login`). Done in NUD-9 with the Firebase MCP: project `nuddle-app-er3n` ("Nuddle"), app "Nuddle debug". `google-services.json` goes to `apps/android/app/src/dev/` and `src/e2e/` (git-ignored).
- [x] `firebase login`.

## Before stage 6

- [ ] At least 16 GB RAM free for two emulators plus Docker, or accept running one emulator at a time.

## Before stage 7

- [x] Buy `nuddle.app` (first year about 9.99 USD, then about 15 USD/year). Bought at Cloudflare on 2026-10-11; DNS stays at Cloudflare. `durt.app` not bought.
- [x] Supabase account, create the cloud project in `eu-central-1` (Frankfurt). `supabase login`. Project `nuddle`, ref `hksqgdkbxalaixjghlvi`.
- [x] Vercel account linked to GitHub. `vercel login`. Project `nuddle` in team `er3ns-projects`.
- [ ] Put the production secrets in files, one raw value per file, never in the repo or a chat: `mkdir -p -m 700 ~/.nuddle/prod`, then for each name `pbpaste | tr -d '\n' > ~/.nuddle/prod/<NAME> && chmod 600 ~/.nuddle/prod/<NAME>`. The agent adds them to Vercel (sensitive, Production only) without reading them.
  - `DATABASE_URL`: Supabase dashboard, Connect, "Transaction pooler" URI (port 6543) with the database password filled in, plus `?sslmode=require`. A password with special characters must be URL-encoded; easier to reset it to a long alphanumeric one.
  - `SUPABASE_SECRET_KEY`: Project Settings, API Keys, a new secret key named `vercel`.
  - `OPENROUTER_API_KEY`: a separate production key with a monthly credit limit.
  - `FIREBASE_SERVICE_ACCOUNT`: an FCM-only service account of the dev project (the prod Firebase app comes before the beta). Google Cloud console, project `nuddle-app-er3n`, IAM & Admin, Service Accounts, create `nuddle-fcm-sender` with the role "Firebase Cloud Messaging API Admin" and no user access. Keys, Add key, JSON. Then `jq -c . <downloaded file> > ~/.nuddle/prod/FIREBASE_SERVICE_ACCOUNT` and delete the download.
  - `SUPABASE_ACCESS_TOKEN`: a personal access token (supabase.com, Account, Access Tokens) with a short expiry, for the Auth settings. Revoke it after stage 7.
- [ ] Link and migrate the cloud project yourself when the agent says the migrations are ready (`supabase/README.md`, Cloud project): `supabase link`, `supabase db push` (never with `--include-seed`).
- [ ] Generate the shared secrets and apply the Auth settings: `mise exec -- supabase/cloud/configure.sh secrets`, then `... auth`. The agent then adds `TICK_SECRET` and `SEND_SMS_HOOK_SECRET` to Vercel from the same files.
- [ ] Approve each Vercel domain and Cloudflare DNS change the agent proposes.
- [ ] Promote the first production deployment yourself: `vercel promote <preview URL> --scope er3ns-projects` (Vercel rebuilds it with the Production env). From then on, merging to `main` deploys production; after an env change, `vercel redeploy <production URL> --target production --scope er3ns-projects`.
- [ ] Once `https://nuddle.app/api/v1/health` is green, switch the tick job on: `mise exec -- supabase/cloud/configure.sh tick`.

## E2E in CI (NUD-30)

- [ ] Add two repository secrets (Settings, Secrets and variables, Actions) so the manual `e2e` workflow can run: `FIREBASE_SERVICE_ACCOUNT` (the same one-line JSON as in `apps/web/.env.local`) and `GOOGLE_SERVICES_JSON_E2E` (the content of `apps/android/app/src/e2e/google-services.json`). From the dev machine: `gh secret set FIREBASE_SERVICE_ACCOUNT < file` and `gh secret set GOOGLE_SERVICES_JSON_E2E < apps/android/app/src/e2e/google-services.json`.
- [ ] Start the first run: Actions, e2e, Run workflow (or `gh workflow run e2e`).

## Before the beta (about 20 families)

- [ ] Register `app.nuddle` (prod) in Firebase (in `nuddle-app-er3n` or a separate project: decide then), put its `google-services.json` in `apps/android/app/src/prod/` (git-ignored), and give Vercel a service account of that project (moved from stage 7, NUD-11).

- [ ] Google Play Console account. Declare the app as an alarm app (`USE_EXACT_ALARM`, full screen intent). Upload an early closed test build to learn the policy outcome.
- [ ] SMS provider account (WhatsApp Business authentication template later). Configure sender, add credentials to env (`TWILIO_*` and, with the signed prod app, `SMS_RETRIEVER_HASH` in Vercel Production).
- [ ] Privacy notice and terms (KVKK): name the cross-border transfer (Supabase Frankfurt, Vercel, Firebase, the LLM provider via OpenRouter: written texts, and for poke planning past task titles, outcomes and connection names, ADR 0017), photo retention (deleted when the task closes), account deletion.
- [ ] Invite links open the app (App Links): put the SHA-256 fingerprint of the release signing certificate (Play App Signing key, and the upload key if you test sideloaded builds) into Vercel Production as `ANDROID_APP_LINK_FINGERPRINTS` (comma separated), redeploy, then check `https://nuddle.app/.well-known/assetlinks.json`. Approve the `durtmobil.com` redirect to `nuddle.app` when the agent proposes it (open-questions #13).
- [ ] Physical test phones: at least one Xiaomi and one Samsung, plus your own.
- [ ] Run the manual checklist in `testing.md` on each beta build.

## Before the global launch

- [ ] Trademark check for Nuddle (US/EU) and the status of `nuddle.com`.
- [ ] English strings in the app (`values-en`). The English landing page is done (NUD-21); read its copy once (`apps/web/src/i18n/messages/en.ts`).
- [ ] Switch Vercel and Supabase to paid plans; add a staging Supabase project.
