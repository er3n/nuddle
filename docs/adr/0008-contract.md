# 0008 Contract: zod → OpenAPI → generated Kotlin client

Status: Accepted (2026-10-11).

## Context
An agent will change a field on the server and forget the client. A human should not be the one to notice.

## Decision
- zod schemas in `apps/web/src/api` are the source. `@hono/zod-openapi` produces the document.
- `mise run contract:gen` writes `packages/contract/openapi.yaml`. The file is committed so contract changes are visible in PR diffs.
- CI regenerates it and fails if the committed file differs.
- `apps/android` generates its client from the yaml at build time with the OpenAPI Generator Gradle plugin (Kotlin, kotlinx.serialization). Generated sources are not committed.
- `redocly lint` runs on the yaml: every operation has an `operationId` (it becomes the Kotlin function name), consistent naming.

## Rejected
- **Hand-written Kotlin DTOs + contract tests:** works, but drift is caught later.
- **Kotlin Multiplatform shared models:** only possible with a Kotlin backend (rejected in ADR 0002).
