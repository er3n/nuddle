# 0018 E2E driven over adb on fake time, without Maestro

Status: Accepted (2026-10-15). Supersedes the E2E driver and its waiting of 0012; the rest of 0012 stands.

## Context
The smoke set took about 20 minutes and the two-person set (NUD-64) about 15. Most of it was waiting: real time until a poke rang (about 70 s each) or a deadline passed, a clean install with sign-up in the app on every run, and 5 to 8 s to start Maestro for every flow (about 30 per smoke run). A loop that slow is run rarely, and agents wait on it. The person asked for fake time and a quicker driver (2026-10-15).

New information since 0012: the orchestrator already sequences the two devices itself, so the rejected "UI Automator cannot drive two devices" no longer applies. Each device only needs to be read and tapped, which `uiautomator dump` and `input` over adb do from the orchestrator.

## Decision
- **Driver:** the TS orchestrator drives each emulator over adb. It reads the screen with `uiautomator dump` and finds elements only by test tag (the `e2e` flavor exposes them as resource ids). It acts with `input tap`, `input text` and key events. Flows are TypeScript functions (`e2e/orchestrator/src/flows.ts`). Text is matched only to check copy. The emulators' on-screen keyboards are turned off, so typed keys go straight into the field without autocorrect.
- **Fake time:** the `e2e` flavor's injected `Clock` is the phone's clock plus an offset, and alarms are set that much earlier (ADR 0009 still holds: time is injected). An e2e-only receiver moves the offset. The orchestrator moves the server clock and both phones together to the next step of a plan, or past a deadline, then runs `tick`. No step waits for time to pass, except where the phone's own clock is the point (a force stop's time).
- **Warm start** for the two-person set: the database and the signed-in apps of the last run are reused when they fit, and tasks are wiped between cases. The smoke set starts clean because it tests sign-up and invite.
- A failed step leaves both phones' screenshots and UI dumps in its folder.

## Consequences
- The smoke set runs in about 8 minutes, the two-person set in about 3 when warm.
- Maestro and its YAML flows are gone, along with the `maestro` tool pin (ADR 0001 listed it).
- A text node UI Automator does not report (one hint under a field) is checked by the Compose screen test instead.
- The `e2e` flavor has a receiver and a clock that other builds lack; a JVM test checks that dev builds have neither.

## Rejected
- **Keep Maestro, fake time only:** smoke about 9 minutes; the Maestro start is a fixed cost per step.
- **A resident on-device driver (like Maestro's or Appium's server)** for faster screen reads than `uiautomator dump`: more moving parts; revisit if the dumps (1 to 2 s each) become the bottleneck.
