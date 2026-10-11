# 0009 Time is always injected

Status: Accepted (2026-10-11).

## Context
Almost every rule in this product is about time: poke steps, quiet hours, deadlines, auto-approve, missed cards, snooze limits, the undo toast. Code that reads the wall clock cannot be tested deterministically, and E2E tests need to move server time forward.

## Decision
- TS: domain code receives `now` or a `Clock` interface. `Date.now()` and argument-less `new Date()` are banned in `src/domain` by ESLint (`no-restricted-syntax`). The real clock exists only in `src/infra/clock.ts`.
- Kotlin: `kotlin.time.Clock` is injected (the standard library type; kotlinx-datetime deprecated its own `Clock` in 0.7 and points to it, NUD-9). detekt `ForbiddenMethodCall` bans `System.currentTimeMillis`, `Instant.now()`, `LocalDateTime.now()`, `Clock.System` outside the DI module.
- Compose tests use `mainClock.advanceTimeBy`. Coroutine tests use virtual time.
- E2E mode exposes `/test/advance-clock` which shifts the server's injected clock.

## Consequences
Every time-related bug gets a deterministic test. Lint makes the rule impossible to forget.
