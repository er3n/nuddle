# Testing

Why: one person reviews agent-written code. Tests are the spec and the reviewer's eyes. An agent should be able to prove a change works without a human opening an emulator.

## Ground rules

1. **Time is injected** (ADR 0009). Never read the wall clock in domain code.
2. **External services are faked** behind interfaces: `PushSender`, `OtpSender`, `TaskExtractor`, `PhotoStorage`, `Clock` on the server; `Clock`, `AlarmScheduler` on Android. Fakes record calls; assertions read the records.
3. **Never edit a test to make it pass.** If a test must change because behavior changed on purpose, say so in the PR description.
4. **Every behavior change ships with a test** at the lowest layer that can catch it. No coverage percentage gate.
5. **Test data comes from builders**: `aTask().forRecipient(eren).withDeadline(...)`, `aPerson("Selin")`. Use the Selin and Eren cast from `glossary.md`.
6. Long commands (Gradle, E2E) run in the background with a timeout. Do not block waiting on them; poll the log.

## Layers

| Layer | Tool | Runs on | What it catches |
|---|---|---|---|
| Server domain unit | Vitest | Node | State machine transitions, `planPokes`, quiet hours shifting, `tick` handlers, extraction post-processing |
| Server integration | Vitest + real Postgres | local Supabase | Repositories, migrations, idempotency keys, `FOR UPDATE SKIP LOCKED`, sync cursor queries |
| HTTP | Hono `app.request()` | Node + Postgres | Auth, validation, status codes, command/sync shapes |
| **Scenario** | Vitest + two simulated clients + controllable clock | Node + Postgres | Whole task lifecycles across sender and recipient |
| LLM eval | script over a golden set | real model | Extraction quality for Turkish free text |
| Android domain | JUnit + kotlinx-coroutines-test | JVM | Plan execution, outbox, quiet hours re-check, sync reducer |
| Android data | Room in-memory on Robolectric, MockWebServer | JVM | DAOs, generated client wiring |
| UI behavior | Compose UI test on Robolectric | JVM | State renders, callbacks fire, undo toast timing with `mainClock` |
| UI screenshots | Roborazzi | JVM | Visual output per state; agent's visual check |
| Accessibility | ATF checks via Robolectric | JVM | Touch targets, content descriptions, contrast |
| Architecture | Konsist | JVM | Layering rules (see `quality.md`) |
| E2E | Maestro + TS orchestrator, two emulators | emulator | Real flows across two devices and system conditions |
| Manual | checklist | real phones | OEM killers, loudness, real OTP delivery |

## Scenario tests (the most valuable layer)

Two simulated clients (Selin, Eren) call the API through the generated TS client (`packages/contract/openapi.d.ts` + `openapi-fetch`, in-process via `app.request()`), each scenario on a fresh copy of a migrated template database. Time moves only through the injected clock and `POST /api/internal/tick`. The fake `PushSender` records what went to whom, by name.

Example:

> Selin writes "eren yarın 9dan önce temizlikçi için 1000 çeksin" → task created for Eren with photo mode → a plan is pushed to Eren's device → three steps fire without response → Selin gets "Eren'e ulaşamıyorum" → Eren sends a photo → status `AWAITING_APPROVAL`, poking stopped → 3 hours pass → auto-approved → Eren gets the done note.

Start the backend scenario suite with: task to self, task to other with done, photo + approve, photo + re-request, can't today with note, missed then "Sonradan yaptım", sender cancel, quiet hours shift, deadline edit regenerates plan, invite accepted / declined (declined invisible to inviter), logout makes reachability `NONE`, account deletion cancels the other side's open tasks.

## LLM eval

- `evals/extraction/golden.jsonl`: about 30 cases to start. Each has raw text, sender time zone, a fixed `now`, the sender's connections, and the expected recipient, title, detail, deadline, finish mode, and whether a question is expected.
- Cover relative times ("akşama", "yarın sabah", "9dan önce", "cuma akşama kadar"), numbers in words ("bin lira"), self tasks, ambiguous recipients, missing deadline.
- `mise run eval` runs it against the real provider (OpenRouter, model from `OPENROUTER_MODEL`) and prints a score. Run when the prompt or model changes. Not part of CI.

## Android UI tests

- Screens are stateless composables (`UiState` + callbacks). ViewModels are tested separately with fake repositories.
- Every component and screen state has a `@Preview`. Roborazzi captures previews automatically.
- **Two phases for screenshots:**
  1. *Record only* while a screen's design is moving: images are produced for the agent and the reviewer, nothing is compared, nothing fails.
  2. *Verify* once a screen is settled: its images become goldens and unintended changes fail the build. Goldens change only via the record task and always show up in review.
- Screenshot matrix per screen: light theme only in the MVP, font scale 1.0 / 1.5 / 2.0, locale `tr` (add `en` before the global launch), small phone (360 dp) and design width (390 dp).
- Compare against the design renders in `docs/ux/screens/*.png` when building a screen.

## E2E

### Backend E2E mode
`E2E=1` enables (and production builds exclude, verified by a test):
- Fixture `TaskExtractor` keyed by input text.
- Fixed OTP codes for test numbers.
- Compressed plans: steps seconds apart instead of minutes.
- `/api/test/reset`, `/api/test/seed`, `/api/test/tick`, `/api/test/advance-clock` (under `/api`, where Hono is mounted).

### Android `e2e` flavor
- Base URL points at the host (`adb reverse tcp:3000 tcp:3000`).
- `testTagsAsResourceId = true` (only in this flavor); every interactive element has a `testTag` from `ui/TestTags.kt`, and flows find elements only by tag.
- CameraX replaced by injecting an image file from the test. Arrives with photo tasks (slice 4); the app has no camera before that.

### Orchestrator
TypeScript runner in `e2e/orchestrator` that drives two emulators (`emulator-5554` as Selin, `emulator-5556` as Eren), runs Maestro flows on each in sequence, and calls test endpoints in between. Every run writes `e2e-runs/<timestamp>/` with per-step screenshots of both devices, logcat for both, and server logs. Agents debug from that folder.

The emulators are AVDs `nuddle-selin` and `nuddle-eren` (`e2e/scripts/emulators.sh`): stock Android with Google Play (`android-36`, `google_apis_playstore`), hardware profile of a Galaxy S23 (1080 x 2340 px, 480 dpi, about 360 x 780 dp). Each run starts from a clean state: a re-created `nuddle_e2e` database and the app reinstalled on both emulators. Prerequisites, the run folder layout and the smoke flows are in `e2e/README.md`.

FCM needs Google Play system images and the dev Firebase app. Flows wait with timeouts because push can be slow.

### System conditions via adb

| Condition | Command |
|---|---|
| Doze | `adb shell dumpsys deviceidle force-idle` |
| Standby bucket | `adb shell am set-standby-bucket app.nuddle.debug rare` |
| Do Not Disturb | `adb shell cmd notification set_dnd on` |
| Force stop | `adb shell am force-stop app.nuddle.debug` |
| Reboot | emulator reboot, then `adb shell dumpsys alarm` |
| Permissions | `adb shell appops set app.nuddle.debug SCHEDULE_EXACT_ALARM deny`, same for `USE_FULL_SCREEN_INTENT` |
| Invite link | `adb shell am start -a android.intent.action.VIEW -d <link>` |
| Offline | emulator network off |

### Smoke set (runs before a PR is merged)
1. Sign up with a test number, write a task to self, full screen poke rings, Done.
2. Selin invites Eren, Eren consents and completes setup, both see each other.
3. Selin writes a photo task to Eren, Eren gets poked, sends a photo, Selin approves.
4. Selin writes a task to Eren, it is his next-up card and his phone sets its alarms; Selin cancels it from the task detail with a reason, Eren's pending alarms are gone.
5. Doze: a `SOUND` step still rings on time.
6. Reboot: pending alarms are re-set.

The full set runs nightly or on demand. Because the repo is public, standard GitHub-hosted Linux runners are free and have KVM, so the smoke set can also run in CI (two emulators on one runner, or one job per device role). Locally remains the default while flows are young.

## Manual checklist (before each beta build, about 15 minutes)

- Xiaomi and Samsung: leave the phone idle for a few hours with a task scheduled; confirm it rings, or that "Son dürtmen çalmadı" appears with the brand steps.
- Full screen poke over the lock screen is loud and annoying enough.
- DND on a real phone: `SOUND` and `FULL_SCREEN` pierce it.
- Real OTP delivery over WhatsApp and SMS.

## Commands

| Command | Contents | When |
|---|---|---|
| `mise run test:fast` | lint (fast), typecheck, server unit, Android domain unit, UI behavior, a11y, screenshot record | after every change, under ~1 minute target |
| `mise run test` | + contract diff, HTTP, scenario, integration, Roborazzi verify, Konsist | before opening a PR, in CI |
| `mise run e2e:smoke` | smoke set on two emulators | before merging, background with timeout |
| `mise run e2e` | full E2E set | nightly or on demand |
| `mise run eval` | LLM golden set | when prompt or model changes |
