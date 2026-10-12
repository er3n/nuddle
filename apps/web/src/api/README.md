# src/api

The Hono app, mounted in `src/app/api/[[...route]]/route.ts` (ADR 0003). See `docs/architecture.md` (API, Sync model) and ADRs 0006, 0008, 0014.

| File                       | What                                                                                        |
| -------------------------- | ------------------------------------------------------------------------------------------- |
| `app.ts`                   | `createApp(deps)`: request id, JWT check, DRT error mapping, routes; `openApiDocument`      |
| `routes.ts`                | Public `/api/v1` routes (contract source)                                                   |
| `schemas.ts`               | zod schemas of requests and responses                                                       |
| `services.ts`              | Load, call the domain, persist in one transaction, then push; sync; the Postgres `TickPort` |
| `internal.ts`              | `/api/internal/tick` and the Send SMS hook                                                  |
| `e2e.ts`                   | Test endpoints and compressed plans; loaded only when `E2E=1`                               |
| `errors.ts`                | `DRT-xxxx` codes                                                                            |
| `deps.ts`, `production.ts` | Injected dependencies and their production wiring from env (`.env.example`)                 |

Adding a command: a schema in `schemas.ts`, a route in `routes.ts`, a service function, then `mise run contract:gen` and commit `packages/contract/`.
