// Everything the API needs from the outside, injected so tests swap fakes in (docs/testing.md,
// Ground rules 2). Production wiring: production.ts.
import type {
  Clock,
  CodeCipher,
  Delay,
  OtpSender,
  PersonProfiler,
  PhotoStorage,
  PokePlanner,
  PushSender,
  TaskExtractor,
} from "../domain/ports";
import type { Outcome } from "../domain/task-machine";
import type { TokenVerifier } from "../infra/auth/jwt";
import type { AuthGateway } from "../infra/auth/supabase-auth";
import type { OffsetClock } from "../infra/clock";
import type { Db } from "../infra/db/store";

interface OtpDelivery {
  /** The 12 s wait before the SMS fallback: a timer in production, released by tests. */
  delay: Delay;
  /**
   * Keeps the function alive for the fallback after the hook answered: Vercel's `waitUntil` in
   * production. Tests collect the work and await it when they choose.
   */
  waitUntil: (work: Promise<unknown>) => void;
  /** Holds the code encrypted for the fallback and "SMS ile gönder". */
  cipher: CodeCipher;
  /** Twilio's auth token, which signs status callbacks; null refuses every callback. */
  twilioAuthToken: string | null;
  /** The public URL Twilio posts statuses to, the one its signature covers. */
  statusUrl: string;
}

export interface AppDeps {
  db: Db;
  clock: Clock;
  push: PushSender;
  otp: OtpSender;
  /** The WhatsApp code's SMS fallback and Twilio's status callback (ADR 0029). */
  otpDelivery: OtpDelivery;
  extractor: TaskExtractor;
  /** The LLM poke planner (ADR 0017); null keeps plan v0 only (E2E mode, most tests). */
  planner: PokePlanner | null;
  /** The LLM person profiler (ADR 0020); null with no planner. */
  profiler: PersonProfiler | null;
  /**
   * Runs work after the response: Next's `after()` in production. Tests await it so scenarios stay
   * deterministic.
   */
  background: (job: () => Promise<void>) => Promise<void> | void;
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
