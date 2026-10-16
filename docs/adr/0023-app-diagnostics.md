# 0023 The app leaves breadcrumbs and non-fatal reports in Crashlytics

Status: Accepted (2026-10-16). Decided in NUD-95. Extends NUD-39 (Crashlytics for crashes and 6f codes).

## Context
On 2026-10-16 a tester tapped Bitti in the first internal-test build and the screen stayed white. Crashlytics had no crash, the server had accepted the done command, and nothing said what the phone did in between. Crashlytics only saw crashes and the 6f code (NUD-39): a failure the app catches and turns into UI state, or swallows, left no trace. The server side does not help after the fact either: Vercel's Hobby plan keeps runtime logs for one hour (the same gap ADR 0019 closed for LLM calls).

## Decision
- **One interface, `Diagnostics`** (Android `domain`), with three things: a breadcrumb line, a non-fatal error, and keys. The Crashlytics implementation lives in `data/diagnostics`; tests use a recording fake. Screens never see it (CLAUDE.md rule 8): the nav host reports route changes through a callback, ViewModels and repositories get it injected.
- **Breadcrumbs** (Crashlytics custom logs, the last 64 KB travel with the next crash or non-fatal): app start, what opened the main activity (launcher, a poke's screen, an invite link without its code), every route change (the route pattern, never its arguments), every API call with its outcome (`api done 200`, `api writeTask 409 DRT-4001`, `api sync network SocketTimeoutException`), every command queued in the outbox, poke and alarm events (fired, skipped, Bitti, Ertele, Bugün olmaz, rearm) and push hints. The same lines go to logcat.
- **Non-fatals** (`recordException`):
  - every API call that fails, except the answers the app expects in normal use: `invalid_otp` and `otp_locked` (a mistyped code), a lost connection and a cancelled call. A timeout is recorded, since it means the server did not answer;
  - errors the app catches and swallows elsewhere: a worker that throws (WorkManager would only mark it failed), an alarm volume the system refuses, a gallery picture that cannot be read. A missing system screen (no dialer, a brand settings page that does not exist on this model) is expected and not recorded.
- **Keys**: version name and code, build flavor, current route. The Crashlytics user id is the opaque person id (JWT `sub`, ADR 0014), set on sign-in and cleared on sign-out.
- **Never recorded**: task text, notes, names, phone numbers, photos or their paths, invite codes, URLs (signed Storage URLs carry tokens), request or response bodies, tokens. Opaque ids (person, task, plan step) are allowed: they are what lets a report be matched with the server's rows. Breadcrumbs are built from code constants and these ids only, so the privacy notice ("not linked to your phone number") stays true.
- **The e2e flavor records nothing**: `Diagnostics` is bound to a logcat-only implementation there (`BuildConfig.CRASH_REPORTS`), on top of `firebase_crashlytics_collection_enabled=false`.
- **Server logs stay as they are** until the beta: one hour on Hobby (open-questions#53).

## Rejected
- **Logging inside every repository and ViewModel**: dozens of call sites to keep in step. One OkHttp interceptor sees every call to our API, reads the call's name from Retrofit's `Invocation` and never the URL.
- **A non-fatal for every network failure**: phones go offline all the time; that is a breadcrumb, not an issue.
- **Free-text logging from anywhere** (`Log.d` style with user content): one careless line puts a task text into a third-party service.
- **A separate analytics or logging SDK**: another processor in the privacy notice for what Crashlytics already does.
