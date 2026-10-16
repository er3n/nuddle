// Interfaces to external services (docs/architecture.md, Layering rules). Real adapters live in
// src/infra, recording fakes in test/fakes.
import type { Extraction, ExtractionInput } from "./extraction";
import type { PlannerInput, PlanProposal } from "./planner";
import type { ProfileInput } from "./profile";
import type { PersonId, PushMessage, TaskId } from "./types";

export interface Clock {
  now(): Date;
}

/** FCM data messages (hints only). */
export interface PushSender {
  send(message: PushMessage): Promise<void>;
}

/** OTP delivery for the Supabase Send SMS hook: WhatsApp template first, SMS fallback. */
export interface OtpSender {
  send(phone: string, code: string): Promise<void>;
}

/** What went to the model and what came back, for the LLM call log (ADR 0019). */
export interface LlmExchange {
  model: string;
  system: string;
  prompt: string;
  /** The answer, parsed when it was JSON, otherwise the text as it came. */
  output: unknown;
  /** Tokens and cost as the provider reported them (ADR 0024); null when it did not. */
  usage?: LlmUsage | null;
}

/** What one call used. `costUsd` is what the provider charged, null when it did not say. */
export interface LlmUsage {
  promptTokens: number;
  completionTokens: number;
  costUsd: number | null;
}

/** Told about the exchange once the model answered; fakes without a model never call it. */
export type ExchangeSink = (exchange: LlmExchange) => void;

/** LLM task extraction (docs/domain.md, LLM task extraction). */
export interface TaskExtractor {
  extract(input: ExtractionInput, onExchange?: ExchangeSink): Promise<Extraction>;
}

/** LLM person profile writer (ADR 0020): a short text on how the person responds to pokes. */
export interface PersonProfiler {
  profile(input: ProfileInput, onExchange?: ExchangeSink): Promise<string>;
}

/** LLM poke planner (ADR 0017). Its answer is checked by `checkPlan` before it is used. */
export interface PokePlanner {
  plan(input: PlannerInput, onExchange?: ExchangeSink): Promise<PlanProposal>;
}

/** Private photo bucket, one path per task. */
export interface PhotoStorage {
  createUploadUrl(taskId: TaskId): Promise<{ path: string; url: string }>;
  /** A short-lived signed URL to look at an uploaded photo (4f). */
  createViewUrl(path: string): Promise<string>;
  deleteForTask(taskId: TaskId): Promise<void>;
  /** A signed upload URL for a profile picture, under `avatars/<person>/` (NUD-72). */
  createAvatarUploadUrl(personId: PersonId): Promise<{ path: string; url: string }>;
  /** One file by path: the previous profile picture. */
  deleteFile(path: string): Promise<void>;
}
