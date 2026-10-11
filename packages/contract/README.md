# packages/contract

Generated from the zod schemas in `apps/web/src/api` by `mise run contract:gen` and committed (ADR 0008). Do not edit by hand.

- `openapi.yaml`: the API contract. `redocly lint` runs in `mise run lint`; the Android client is generated from it at build time (stage 5).
- `openapi.d.ts`: TypeScript types for `openapi-fetch`, used by the scenario and HTTP tests.

`mise run test` regenerates both and fails if they differ from the committed files.
