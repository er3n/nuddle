// Everything the API needs from the outside, injected so tests swap fakes in (docs/testing.md,
// Ground rules 2). Production wiring: production.ts.
import type { Clock, OtpSender, PhotoStorage, PushSender, TaskExtractor } from "../domain/ports";
import type { Outcome } from "../domain/task-machine";
import type { TokenVerifier } from "../infra/auth/jwt";
import type { AuthGateway } from "../infra/auth/supabase-auth";
import type { OffsetClock } from "../infra/clock";
import type { Db } from "../infra/db/store";

export interface AppDeps {
  db: Db;
  clock: Clock;
  push: PushSender;
  otp: OtpSender;
  extractor: TaskExtractor;
  photos: PhotoStorage;
  tokens: TokenVerifier;
  auth: AuthGateway;
  /** Bearer secret for POST /api/internal/tick (pg_cron, `mise run tick`). */
  tickSecret: string;
  /** Standard Webhooks secret ("v1,whsec_...") of the Supabase Send SMS hook. */
  smsHookSecret: string;
  /** Shown by GET /api/v1/health: the short Git commit of the deployment. */
  version: string;
  /** E2E mode only (docs/architecture.md, E2E mode): compressed plans and the shiftable clock. */
  e2e: { clock: OffsetClock; adjustOutcome: (o: Outcome, now: Date) => Outcome } | null;
}
