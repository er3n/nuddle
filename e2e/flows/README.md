# e2e/flows

Maestro YAML per device role (`selin/`, `eren/`), run by the orchestrator on that role's emulator. Flows find elements only by test tag (`apps/android/app/src/main/kotlin/app/nuddle/ui/TestTags.kt`); the `e2e` flavor exposes tags as resource ids, so a flow uses `id: <tag>`. Never match on visible text: copy changes.

One flow per step the orchestrator needs to observe in between (screenshots, server checks), not one per scenario. Push and alarms can be slow: wait with `extendedWaitUntil` and a timeout, never with fixed sleeps.
