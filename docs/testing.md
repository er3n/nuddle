# Testing

Why: one person reviews agent-written code. Tests are the spec and the reviewer's eyes. An agent should be able to prove a change works without a human opening an emulator.

## Ground rules

1. **Time is injected** (ADR 0009). Never read the wall clock in domain code.
2. **External services are faked** behind interfaces: `PushSender`, `OtpSender`, `TaskExtractor`, `PhotoStorage`, `Clock` on the server; `Clock`, `AlarmScheduler` on Android. Fakes record calls; assertions read the records.
3. **Never edit a test to make it pass.** If a test must change because behavior changed on purpose, say so in the PR description.
4. **Every behavior change ships with a test** at the lowest layer that can catch it. No coverage percentage gate.
5. **Test data comes from builders**: `aTask().forRecipient(eren).withDeadline(...)`, `aPerson("Sibel")`. Use the Sibel and Eren cast from `glossary.md`.
6. Long commands (Gradle, E2E) run in the background with a timeout. Do not block waiting on them; poll the log.

## Layers

| Layer | Tool | Runs on | What it catches |
|---|---|---|---|
| Server domain unit | Vitest | Node | State machine transitions, `planPokes`, `checkPlan`, quiet hours shifting, `tick` handlers, extraction post-processing |
| Server integration | Vitest + real Postgres | local Supabase | Repositories, migrations, idempotency keys, `FOR UPDATE SKIP LOCKED`, sync cursor queries |
| HTTP | Hono `app.request()` | Node + Postgres | Auth, validation, status codes, command/sync shapes |
| **Scenario** | Vitest + two simulated clients + controllable clock | Node + Postgres | Whole task lifecycles across sender and recipient |
| LLM eval | script over a golden set | real model | Extraction quality for Turkish free text, and poke plans (ADR 0017) |
| Android domain | JUnit + kotlinx-coroutines-test | JVM | Plan execution, outbox, quiet hours re-check, sync reducer |
| Android data | Room in-memory on Robolectric, MockWebServer | JVM | DAOs, generated client wiring |
| Web pages | Vitest + `react-dom/server` (unit project, `*.test.tsx`) | Node | Landing and invite page render per locale, dictionaries match (en has every tr entry), locale negotiation |
| UI behavior | Compose UI test on Robolectric | JVM | State renders, callbacks fire, undo toast timing with `mainClock` |
| UI screenshots | Roborazzi | JVM | Visual output per state; agent's visual check |
| Accessibility | ATF checks via Robolectric | JVM | Touch targets, content descriptions, contrast |
| Architecture | Konsist | JVM | Layering rules (see `quality.md`) |
| E2E | TS orchestrator over adb on fake time, two emulators (ADR 0018) | emulator | Real flows across two devices and system conditions |
| Manual | checklist | real phones | OEM killers, loudness, real OTP delivery |

## Scenario tests (the most valuable layer)

Two simulated clients (Sibel, Eren) call the API through the generated TS client (`packages/contract/openapi.d.ts` + `openapi-fetch`, in-process via `app.request()`), each scenario on a fresh copy of a migrated template database. Time moves only through the injected clock and `POST /api/internal/tick`. The fake `PushSender` records what went to whom, by name.

Example:

> Sibel writes "eren yarın 9dan önce temizlikçi için 1000 çeksin" → task created for Eren with photo mode → a plan is pushed to Eren's device → three steps fire without response → Sibel gets "Eren'e ulaşamıyorum" → Eren sends a photo → status `AWAITING_APPROVAL`, poking stopped → 3 hours pass → auto-approved → Eren gets the done note.

Start the backend scenario suite with: task to self, task to other with done, photo + approve, photo + re-request, can't today with note, missed then "Sonradan yaptım", sender cancel, quiet hours shift, deadline edit regenerates plan, invite accepted / declined (declined invisible to inviter), logout makes reachability `NONE`, account deletion cancels the other side's open tasks, erases the number at once so it can sign up again, and leaves no row with the person after the week's purge, a quiet hours or human channel switch change replans open tasks, removing a connection cancels open tasks both ways without a notification, a poke whose alarm did not ring while the phone was seen online is lost and reaches only that phone (an offline phone is not evidence), a Bitti tapped offline before the deadline still counts when it arrives after it (and one tapped after it does not), a text queued offline and sent twice with its key makes one task.

## LLM eval

- `evals/extraction/golden.jsonl`: about 30 cases to start. Each has raw text, sender time zone, a fixed `now`, the sender's connections, and the expected recipient, title, detail, deadline, finish mode, and whether a question is expected.
- Cover relative times ("akşama", "yarın sabah", "9dan önce", "cuma akşama kadar"), numbers in words ("bin lira"), self tasks, ambiguous recipients, missing deadline.
- `mise run eval` runs it against the real provider (OpenRouter, model from `OPENROUTER_MODEL`) and prints a score. Run when the prompt or model changes. Not part of CI.
- `evals/stories/sentences.jsonl`: 100 everyday sentences with a fixed cast and clock, no expected values. `mise run stories` writes what was understood, plan v0, the model's proposal and what `checkPlan` kept for each, with latencies. It is how plans are judged by eye and how the prompt gets fixed (NUD-70); cases with expected values graduate to the golden sets.
- Real use is the best source of cases: `mise run golden:export` writes every production task with what the LLM was given and answered and what happened (ADR 0019). A case taken from it keeps the text and the expected result and changes names where needed (the repo is public).

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
- `/api/test/reset`, `/api/test/reset-tasks` (tasks only, people and connections stay), `/api/test/seed`, `/api/test/tick`, `/api/test/advance-clock` (under `/api`, where Hono is mounted).

### Android `e2e` flavor
- Base URL points at the host (`adb reverse tcp:3000 tcp:3000`).
- Fake time (`BuildConfig.FAKE_TIME`, NUD-65): the app's injected `Clock` is the phone's clock plus an offset (`di/TestTime.kt`), and alarms are set that much earlier on the phone's clock. The orchestrator moves it with `am broadcast -n app.nuddle.debug/app.nuddle.e2e.E2eReceiver -a app.nuddle.e2e.ADVANCE --el ms <n>`: the app re-arms, so a step that came due rings at once through `AlarmManager`. `app.nuddle.e2e.RESET` empties the app's tables, sets the offset back to 0 and syncs in full. The receiver is declared only in this flavor's manifest; other builds have neither (verified by a test).
- `testTagsAsResourceId = true` (only in this flavor); every interactive element has a `testTag` from `ui/TestTags.kt`, and flows find elements only by tag.
- CameraX replaced by injecting an image file from the test: the orchestrator copies `e2e/fixtures/photo.jpg` into the app's `files/e2e-photo.jpg` (`run-as`), and the camera of this flavor (`InjectedPhotoSource`, `BuildConfig.INJECTED_PHOTO`) returns it as the shot.
- Speech recognition replaced the same way: the orchestrator writes the text to `files/e2e-speech.txt`, and the speech input of this flavor (`InjectedSpeechInput`, `BuildConfig.INJECTED_SPEECH`) "hears" it and ends by itself 1.5 s later, like the real recognizer after silence (NUD-60), or at once on a tap on stop.
- `adb reverse` also forwards `:54321` (local Supabase), where the signed photo upload and view URLs point.

### Orchestrator
TypeScript runner in `e2e/orchestrator` that drives two emulators (`emulator-5554` as Sibel, `emulator-5556` as Eren), runs the flows (`src/flows.ts`) on each in sequence over adb, and calls test endpoints in between (ADR 0018). A flow reads the screen with `uiautomator dump`, finds elements only by test tag (text only to check copy), and acts with `input tap` and `input text`; the emulators' on-screen keyboards are off, so keys go straight into the field. Time is fake (below): a flow never waits for a poke or a deadline, it moves the server and both phones to it. Every run writes `e2e-runs/<timestamp>/` with per-step screenshots and UI dumps of both devices, logcat for both, and server logs. Agents debug from that folder.

The emulators are AVDs `nuddle-sibel` and `nuddle-eren` (`e2e/scripts/emulators.sh`): stock Android with Google Play (`android-36`, `google_apis_playstore`), hardware profile of a Galaxy S23 (1080 x 2340 px, 480 dpi, about 360 x 780 dp). A run of the smoke set starts from a clean state: a re-created `nuddle_e2e` database and the app reinstalled on both emulators. The two-person set starts warm when it can (below). Prerequisites, the run folder layout and the smoke flows are in `e2e/README.md`.

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
2. Sibel invites Eren; Eren opens the link before he has an account (0b, name prefilled on 0e), signs up, consents and completes setup, both see each other.
3. Sibel writes a photo task to Eren, Eren gets poked, Bitti on the full screen poke opens the camera, he sends a photo and his pokes stop, Sibel approves.
4. Sibel writes a task to Eren, it is his next-up card and his phone sets its alarms; Sibel cancels it from the task detail with a reason, Eren's pending alarms are gone.
5. Doze: a `SOUND` step still rings on time.
6. Reboot: pending alarms are re-set.
7. Sibel writes a task to Eren, the full screen poke rings, Eren answers Bugün yapamayacağım with a ready note and his pokes stop; Sibel sees it dropped with the note, gives a new time, and Eren's phone has alarms again.
8. Eren opens profile, then Sibel, and removes the connection: the task from smoke 7 is cancelled with no notification, Eren's alarms for it go, and both sides see the connection as removed.
9. Eren writes a task to himself and his app is force stopped before its pokes: the alarms are gone; on the next launch the bell shows "Son dürtmen çalmadı" and "Düzelt" opens the brand steps (2h).
10. Eren loses his connection: home shows the offline banner (6a) and a text he writes waits as "Sırada"; when the connection is back it goes out by itself and the task is on home.
11. Sibel taps the mic and speaks a task (4h): the transcript goes into the box and the send arrow creates the task.

### Two-person set (`mise run e2e:sync`, NUD-64)
Each case is one thing that happens between a sender and a recipient, checked on both phones: what home shows (test tags from a UI Automator dump), the pending poke alarms and the notifications that are up, and the task on the server. The step a person takes is done in their app (a tap found by its tag in the same dump); the other phone is only watched. Before every case the tasks are wiped on the server (`reset-tasks`) and on both phones (`RESET`).

**Fake time.** No case waits for a poke or a deadline. The orchestrator moves the server's clock and both phones' clocks by the same amount, to just after the next step of the task's current plan (read from `poke_steps`) or past its deadline, then runs the server's timers (`tick`).

**Warm start.** A run reuses the `nuddle_e2e` database and the installed, signed-in apps of the last one when the database was made from today's migrations, Sibel and Eren are connected in it and both apps have a session; the APK is replaced with `install -r` (data kept) only when it changed. Otherwise, or with `--fresh`, it starts clean and signs both up in the app. A warm run takes about 3 minutes.
1. Sibel writes a task while Eren's app is in the background: his alarms and the arrival note, her row; he opens the app and it is next up.
2. Eren taps Şimdiden bitti: done on the server, his card, alarms and notifications go, her row goes.
3. A poke is up on Eren's phone (app in the background) and Sibel cancels the task in the app: the poke and the alarms go from his phone, her row goes, his home says she cancelled it.
4. The full screen poke rings and Eren taps Ertele: counted on the server, his card says when it rings again, his alarms stay, her row stays; Bitti then closes it on both.
5. Eren answers Bugün yapamayacağım: his pokes stop, Sibel's home has the dropped card; Ben hallederim closes it, opens the same task in her own list, and his phone shows "Sibel üstlendi".
6. Nobody answers until the deadline: missed on the server, the poke goes from his phone, both homes have the missed card; Sonradan yaptım closes it on both.
7. Sibel moves a far deadline close: Eren's phone takes the new plan and rings its first step; her cancel then leaves nothing on his phone.

It runs on demand and before merging a change to sync, the outbox, alarms or notifications.

The full set runs on demand. Because the repo is public, standard GitHub-hosted Linux runners are free and have KVM, so the smoke set also runs in CI: the `e2e` workflow (`.github/workflows/e2e.yml`, started by hand from the Actions tab, decided in NUD-30) boots both emulators on one runner with the x86_64 image and uploads `e2e-runs/` as an artifact. Locally `mise run e2e:smoke` remains the gate before merging.

## Manual checklist (before each beta build, about 15 minutes)

- Xiaomi and Samsung: leave the phone idle for a few hours with a task scheduled; confirm it rings, or that "Son dürtmen çalmadı" appears with the brand steps.
- Full screen poke over the lock screen is loud and annoying enough.
- DND on a real phone: `SOUND` and `FULL_SCREEN` pierce it.
- Direct boot on a phone with a PIN: reboot with a task's next step a few minutes away and do not unlock; the poke rings ("Bir dürtmen var"), and after the unlock it shows in full with its buttons (NUD-125).
- Real OTP delivery over WhatsApp and SMS.

## Commands

| Command | Contents | When |
|---|---|---|
| `mise run test:fast` | lint (fast), typecheck, server unit, Android domain unit, UI behavior, a11y, screenshot record | after every change, under ~1 minute target |
| `mise run test` | + contract diff, HTTP, scenario, integration, Roborazzi verify, Konsist | before opening a PR, in CI |
| `mise run e2e:smoke` | smoke set on two emulators | before merging, background with timeout |
| `mise run e2e:sync` | two-person set on two emulators | before merging a change to sync, the outbox, alarms or notifications |
| `mise run e2e` | full E2E set | nightly or on demand |
| `mise run eval` | LLM golden set | when prompt or model changes |
| `mise run stories` | 100 everyday sentences (`evals/stories/sentences.jsonl`) through the real extractor and planner; output in git-ignored `evals/stories/out/` (NUD-70) | when the prompts or the model change, before judging plans |
| `mise run golden:export` | Every production task with its LLM calls, read-only, to git-ignored `golden/` (ADR 0019) | when building eval cases from real use |
