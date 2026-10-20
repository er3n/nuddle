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

- [x] Google Play Console account. Personal account on `dokukoza@gmail.com`, created 2026-10-16 (the 2024 account on another address was closed for inactivity). A new personal account needs a closed test with at least 12 testers opted in for 14 days in a row before production access; the beta families are that test. Done 2026-10-16: the app `app.nuddle` exists, and release 1 (0.1.0) is on internal testing for the tester list "Bizimkiler".
- [x] Play upload key, created on your machine and never shown to the agent:
  - `mkdir -p -m 700 ~/.nuddle/release && keytool -genkeypair -v -keystore ~/.nuddle/release/upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
  - Add to `~/.gradle/gradle.properties` (not the repo's): `nuddle.upload.storeFile=<absolute path to upload.jks>`, `nuddle.upload.storePassword=...`, `nuddle.upload.keyAlias=upload`, `nuddle.upload.keyPassword=...`.
  - Keep a copy of `upload.jks` and both passwords in your password manager. Play App Signing holds the real signing key; a lost upload key can be reset in Play Console, but it takes days.
  - `mise run android:bundle` builds `apps/android/app/build/outputs/bundle/prodRelease/app-prod-release.aab`. Raise `versionCode` in `apps/android/app/build.gradle.kts` before each upload after the first.
- [x] Play uploads from the command line (ADR 0026, NUD-117), once (done 2026-10-20):
  - In Google Cloud project `nuddle-app-er3n-512208` (signed in as dokukoza@), enable the Google Play Android Developer API, create the service account `play-publisher` with no project roles, and download a JSON key.
  - `mv` the key to `~/.nuddle/release/play-service-account.json` and `chmod 600` it. It never goes into the repo.
  - In Play Console (dokukoza@), Users and permissions, invite `play-publisher@nuddle-app-er3n-512208.iam.gserviceaccount.com` as admin (done 2026-10-20; narrowing it to Release to testing tracks and Manage testing tracks is enough for `android:publish`).
  - From then on a release is: raise `versionCode`, write `apps/android/app/src/prod/play/release-notes/<locale>/default.txt`, `mise run android:publish`.
- [ ] Declare the app as an alarm app (`USE_EXACT_ALARM`, full screen intent) and justify `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with the same core function (ringing on time). Upload an early closed test build to learn the policy outcome.
- [ ] SMS provider account (WhatsApp Business authentication template later). Configure sender, add credentials to env (`TWILIO_*` and, with the signed prod app, `SMS_RETRIEVER_HASH` in Vercel Production).
- [ ] Privacy notice and terms (KVKK): name the cross-border transfer (Supabase Frankfurt, Vercel, Firebase, Twilio for the sign-in SMS, the LLM provider via OpenRouter: written texts, and for poke planning past task titles, outcomes and connection names, ADR 0017), photo retention (deleted when the task closes), account deletion. The privacy notice and the deletion page are drafted (NUD-85, `nuddle.app/privacy` and `/delete-account`); read both in Turkish before merging, and confirm open-questions #50 to #52. Terms are still to write.
- [x] `privacy@nuddle.app` receives mail: Cloudflare Email Routing forwards it to your Gmail (enabled 2026-10-16; MX, SPF and DKIM records added by Cloudflare, one rule for this address, catch-all off). Both legal pages name it. A deletion request by mail: confirm the number is theirs (call or SMS it), then `mise run account:delete -- +905xxxxxxxxx --prod` (dry run), the same with `--yes`, and answer within 30 days.
- [x] Play review account (ADR 0022): the number is `905000000001` (`+90 500 000 00 01`, unallocated); write `905000000001=<6 random digits>` to `~/.nuddle/prod/PLAY_REVIEW_OTP` (`chmod 600`), run `mise exec -- supabase/cloud/configure.sh auth` (it shows `"sms_test_numbers": 1`), then enter the number and code in Play Console, App access. To revoke, empty the file and run it again. Applied 2026-10-16, valid until 2027-01-14: run `configure.sh auth` again before then to renew.
- [x] Play Console: privacy policy URL `https://nuddle.app/tr/privacy`, account deletion URL `https://nuddle.app/tr/delete-account`, and the Data safety form filled from the same text. Done 2026-10-16, with the content rating (IARC), target audience 13+, ads, App access and store listing.
- [x] Invite links open the app (App Links): put the SHA-256 fingerprint of the release signing certificate (Play App Signing key, and the upload key if you test sideloaded builds) into Vercel Production as `ANDROID_APP_LINK_FINGERPRINTS` (comma separated), redeploy, then check `https://nuddle.app/.well-known/assetlinks.json`. Approve the `durtmobil.com` redirect to `nuddle.app` when the agent proposes it (open-questions #13). Set 2026-10-16: Play App Signing `0F:2B:…:BA:26`, upload key `EC:F1:…:CE:36` and the earlier `47:19:…:2D:02`; `SMS_RETRIEVER_HASH` is set from the Play App Signing certificate in the same step.
- [ ] Physical test phones: at least one Xiaomi and one Samsung, plus your own.
- [ ] Run the manual checklist in `testing.md` on each beta build.

## Cost limits (ADR 0024)

The code caps each person; these cap the whole bill if the code is wrong. Check them again before the beta and at the global launch.

- [ ] OpenRouter: pay by prepaid credit only, no auto top-up. On the production key set a credit limit (for the beta about 10 USD a month), and an alert e-mail when the balance is low.
- [ ] Twilio: SMS Geo permissions allow only Turkey (open-questions #45). Auto-recharge off. A balance alert (Console, Billing, Usage triggers) at a level you would notice, for example 5 USD, and a usage trigger on SMS count per day.
- [ ] Vercel: Settings, Billing, Spend Management on, with a monthly amount and "pause projects" when it is reached (Pro plan; on Hobby the free limits stop the project).
- [ ] Supabase: Spend cap on (the default on Pro) so usage above the plan stops instead of billing.
- [ ] Apply the Supabase Auth SMS limits in `supabase/cloud/auth.json`: `mise exec -- supabase/cloud/configure.sh auth` (NUD-97).

## Before the global launch

- [ ] Trademark check for Nuddle (US/EU) and the status of `nuddle.com`.
- [ ] English strings in the app (`values-en`). The English landing page is done (NUD-21); read its copy once (`apps/web/src/i18n/messages/en.ts`).
- [ ] Switch Vercel and Supabase to paid plans; add a staging Supabase project.
