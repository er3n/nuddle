# 0004 Native Android first, iOS later

Status: Accepted (2026-10-10).

## Context
The product lives or dies on alarms that ring on time, over the lock screen, through Do Not Disturb. That needs `AlarmManager.setAlarmClock`, full screen intents, notification channels on the alarm stream, boot receivers and OEM specific handling.

## Decision
- Android in Kotlin + Jetpack Compose first. Min SDK 26.
- iOS later in Swift with AlarmKit. No cross-platform UI framework.
- Business logic that must exist on the device (plan execution, outbox, quiet hours re-check) is plain Kotlin in a `domain` package so it can be unit tested on the JVM and, if useful later, moved to a KMP module.

## Rejected
- **React Native / Flutter:** alarm, full screen and OEM work is native anyway; a bridge adds a layer agents and the reviewer must understand.
- **KMP shared UI:** not needed for one platform.
