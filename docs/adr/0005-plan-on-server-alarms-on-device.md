# 0005 Poke plan generated on the server, executed on the device

Status: Accepted (2026-10-11).

## Context
The plan is hidden and learned from behavior across devices and people, so it belongs on the server. But pokes must ring on time even offline, and push delivery is not reliable enough to be the alarm itself.

## Decision
- The server generates the plan (`planPokes`, pure function) and stores it as versioned `poke_steps`.
- The plan reaches the recipient's device through a high priority FCM data message and through sync.
- The device stores steps in Room and sets one `setAlarmClock` alarm per step. Alarms are re-set on boot, package replace, time and time zone changes.
- On fire, the device re-checks quiet hours and plan version, rings at the right level, and reports planned vs actual time.
- Done, Snooze and Can't today stop local alarms immediately, then sync.
- A late cancel push can let a cancelled task ring once. Accepted for the MVP. The fired report lets the server reply with a cancel.

## Consequences
- Server-side timers (deadline, approval timeout, human channel, lost poke check) are separate from device alarms (ADR 0007).
- E2E tests control time by asking the server for compressed plans (seconds apart), since device alarms follow the real clock.

## Rejected
- **Push as the poke:** FCM can be delayed or deprioritized; offline means silence.
- **Plan computed on the device:** learning and the human channel need cross-person data.
