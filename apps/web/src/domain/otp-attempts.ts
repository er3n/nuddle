// Code attempts per phone (ADR 0011, docs/domain.md Time rules, screen 6c): a few wrong codes,
// then a 15 minute wait in which no code is checked or sent (SMS pumping protection).
// TODO(open-questions#41): 3 attempts; a new code does not reset the count.
import { MINUTE } from "./time";

export const OTP_MAX_ATTEMPTS = 3;
export const OTP_LOCK_MS = 15 * MINUTE;

export interface OtpAttempts {
  /** Wrong codes since the last sign-in or the end of the last wait. */
  failures: number;
  lockedUntil: Date | null;
}

export const noAttempts: OtpAttempts = { failures: 0, lockedUntil: null };

/** The end of the wait, while it lasts. */
export function lockedUntil(state: OtpAttempts, now: Date): Date | null {
  return state.lockedUntil !== null && state.lockedUntil.getTime() > now.getTime()
    ? state.lockedUntil
    : null;
}

/** One more wrong code. A wait that is over starts the count again. */
export function afterWrongCode(
  state: OtpAttempts,
  now: Date,
): { state: OtpAttempts; remaining: number } {
  const failures = (state.lockedUntil === null ? state.failures : 0) + 1;
  if (failures >= OTP_MAX_ATTEMPTS) {
    return {
      state: { failures: 0, lockedUntil: new Date(now.getTime() + OTP_LOCK_MS) },
      remaining: 0,
    };
  }
  return { state: { failures, lockedUntil: null }, remaining: OTP_MAX_ATTEMPTS - failures };
}
