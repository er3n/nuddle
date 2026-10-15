# 0012 Testing strategy with E2E mode and Maestro

Status: Accepted (2026-10-11). The E2E driver (Maestro) and its real-time waiting are superseded by 0018.

## Context
One person reviews agent-written code. Manual validation is the bottleneck. Agents must be able to verify their own work, including real device behavior, and debug failures from artifacts.

## Decision
Summary (details in `docs/testing.md`):
- Domain unit tests and backend **scenario tests** with two simulated clients and a controllable clock are the main safety net.
- Android: JVM tests (domain, Room on Robolectric, Compose behavior tests, Roborazzi screenshots, accessibility checks), no emulator needed.
- **E2E:** Maestro flows on two emulators, driven by a TS orchestrator, against the local backend in E2E mode (fixture LLM, test OTP, compressed plans, test endpoints). Android `e2e` flavor with test tags exposed as resource ids and camera replaced by file injection.
- System conditions (Doze, standby buckets, DND, force stop, reboot, permission toggles, offline) are simulated with `adb`.
- Remaining manual checks: OEM battery killers on real Xiaomi/Samsung devices, real loudness, real OTP delivery. A 15 minute checklist before each beta build.
- No coverage percentage gate.

## Rejected
- **Espresso / UIAutomator for E2E:** cannot drive two devices.
- **Appium:** too heavy for a one-person team.
- **Paparazzi:** cannot capture after interactions; Roborazzi can, and also scans previews.
- **Emulator-only manual testing:** does not scale with agent output.
