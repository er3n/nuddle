# apps/android

Independent Gradle build (Kotlin, Compose), `applicationId app.nuddle`, flavors `dev`, `e2e`, `prod` (ADR 0004). Use the `mise` tasks from the repo root; they work the same for humans, agents and CI.

| Task | What it runs |
|---|---|
| `mise run android:test` | `:app:testDevDebugUnitTest`: domain, data (Room, MockWebServer), ViewModels, Compose UI + ATF, Konsist, Roborazzi (record only). Part of `test:fast` and `test`. |
| `mise run lint` | includes `spotlessCheck`, `:app:detekt` and `:app:lintDevDebug` |
| `mise run fix` | includes `spotlessApply` (ktlint) |

## Modules

- `api`: the client generated from `packages/contract/openapi.yaml` at build time (ADR 0008). Not committed.
- `app`: everything else. Packages:
  - `domain`: plain Kotlin, no `android.*` (Konsist): phone numbers, quiet hours, poke execution rules, outbox policy, ids.
  - `data`: Room (tasks, poke steps, outbox), DataStore session, the API wiring (token refresh), sync, outbox worker.
  - `alarm`: `AlarmScheduler` on `setAlarmClock`, receivers (fire, actions, boot and time changes), channels, the poke notification (3a), `PokeActivity` (3b).
  - `push`: FCM service, device registration and the permission summary.
  - `ui`: theme from `docs/ux/tokens.json`, icons, components (`docs/ux/components.md`), screens, navigation.
  - `di`: Hilt modules; the only place the real clock exists (ADR 0009).

## Local setup

- JDK and `ANDROID_HOME` come from `mise.toml` (the SDK defaults to `~/Library/Android/sdk`).
- `google-services.json` for `app.nuddle.debug` (Firebase project `nuddle-app-er3n`) goes to `app/src/dev/` and `app/src/e2e/`. It is git-ignored. Without it the app builds and runs, and FCM registration is skipped.
- Run against the local API: `mise run dev`, then `adb reverse tcp:3000 tcp:3000` and install the `devDebug` build. Test numbers and the code are in `supabase/config.toml`.

## E2E

The `e2e` flavor is `devDebug` plus test tags exposed as resource ids (`BuildConfig.EXPOSE_TEST_TAGS`, `ui/TestTags.kt`), so the E2E flows (`e2e/orchestrator/src/flows.ts`) find elements by tag. Every interactive element gets a tag; `TestTagsTest` checks them. Run with `mise run e2e:smoke` (`e2e/README.md`).

## Screenshots

Roborazzi records every `@Preview` into `app/screenshots/` (record only while designs move, `docs/testing.md`). Screens use `@ScreenPreviews` (390 and 360 dp, font scale 1, 1.5, 2); components use `@ComponentPreview`.

Fonts: `fonts/README.md`.
