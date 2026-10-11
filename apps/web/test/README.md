# test

Vitest projects (docs/testing.md, Layers):

| Folder | Project | Runs in |
|---|---|---|
| `unit/` | `unit` | `mise run test:fast` |
| `scenario/` | `scenario` | `mise run test` |
| `integration/` | `integration` | `mise run test` (needs local Supabase) |
| `eval/` | `eval` | `mise run eval` only |

`fakes/` holds the recording fakes (`FakeClock`, `RecordingPushSender`, `RecordingOtpSender`, `FakePhotoStorage`), `builders.ts` the Selin and Eren builders. The scenario harness (`scenario/harness.ts`) is an in-memory server with simulated phones; it moves to HTTP and Postgres in stage 4.
