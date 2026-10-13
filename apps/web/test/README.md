# test

Vitest projects (docs/testing.md, Layers):

| Folder         | Project       | Runs in                                |
| -------------- | ------------- | -------------------------------------- |
| `unit/`        | `unit`        | `mise run test:fast`                   |
| `http/`        | `http`        | `mise run test` (needs local Supabase) |
| `scenario/`    | `scenario`    | `mise run test` (needs local Supabase) |
| `integration/` | `integration` | `mise run test` (needs local Supabase) |
| `eval/`        | `eval`        | `mise run eval` only                   |

- `fakes/`: recording fakes (`FakeClock`, `RecordingPushSender`, `RecordingOtpSender`, `FakePhotoStorage`, `FakeAuthGateway`). `builders.ts`: the Sibel and Eren builders.
- `support/`: test databases (`db.ts`; HTTP and scenario tests copy a template migrated once per run in `global-setup.ts`), the API under test with test-signed JWTs (`server.ts`), and the local Supabase keys (`local-supabase.ts`).
- `scenario/harness.ts`: simulated phones calling the real API through the generated client; reads come from a database snapshot after every action, with people named "sibel", "eren".
