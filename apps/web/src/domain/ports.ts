// Interfaces to external services (docs/architecture.md, Layering rules). Real adapters live in
// src/infra, recording fakes in test/fakes.
import type { Extraction, ExtractionInput } from "./extraction";
import type { PlannerInput, PlanProposal } from "./planner";
import type { PushMessage, TaskId } from "./types";

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

/** LLM task extraction (docs/domain.md, LLM task extraction). */
export interface TaskExtractor {
  extract(input: ExtractionInput): Promise<Extraction>;
}

/** LLM poke planner (ADR 0017). Its answer is checked by `checkPlan` before it is used. */
export interface PokePlanner {
  plan(input: PlannerInput): Promise<PlanProposal>;
}

/** Private photo bucket, one path per task. */
export interface PhotoStorage {
  createUploadUrl(taskId: TaskId): Promise<{ path: string; url: string }>;
  /** A short-lived signed URL to look at an uploaded photo (4f). */
  createViewUrl(path: string): Promise<string>;
  deleteForTask(taskId: TaskId): Promise<void>;
}
