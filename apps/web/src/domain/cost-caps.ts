// Daily caps on what one person or one phone can spend (ADR 0024): LLM calls per person and
// sign-in codes per phone and in total, counted over the last 24 hours.
// TODO(open-questions#54): the numbers are defaults; a real person should never meet one.
import { addMs, DAY } from "./time";

export const CAP_WINDOW_MS = DAY;

/** LLM calls per person in the window: by the writer, for the recipient, about the person. */
export const LLM_DAILY_CAPS = { extraction: 100, plan: 200, profile: 10 } as const;

export type LlmCallKind = keyof typeof LLM_DAILY_CAPS;

/** Sign-in codes sent to one phone in the window. */
export const SMS_PHONE_DAILY_CAP = 5;

/** Sign-in codes sent to all phones together in the window. */
export const SMS_TOTAL_DAILY_CAP = 200;

/** The uses that count toward a cap: how many in the window, and when the oldest was. */
export interface CapWindow {
  count: number;
  oldest: Date | null;
}

/** The start of the window that ends now; a use exactly this old no longer counts. */
export function capWindowStart(now: Date): Date {
  return addMs(now, -CAP_WINDOW_MS);
}

/** Null while under the cap; at or over it, when the oldest use leaves the window. */
export function capReachedUntil(window: CapWindow, cap: number, now: Date): Date | null {
  if (window.count < cap) return null;
  return addMs(window.oldest ?? now, CAP_WINDOW_MS);
}

/** The later of two waits, or the one there is. */
export function laterOf(a: Date | null, b: Date | null): Date | null {
  if (a === null) return b;
  if (b === null) return a;
  return a.getTime() >= b.getTime() ? a : b;
}
