// Code attempts per phone (screen 6c, open-questions#41).
import { describe, expect, it } from "vitest";
import {
  afterWrongCode,
  lockedUntil,
  noAttempts,
  OTP_LOCK_MS,
  OTP_MAX_ATTEMPTS,
} from "../../src/domain/otp-attempts";
import { MINUTE } from "../../src/domain/time";

const now = new Date("2026-10-11T20:00:00+03:00");
const later = (ms: number) => new Date(now.getTime() + ms);

describe("otp attempts", () => {
  it("counts down, then waits 15 minutes", () => {
    let state = noAttempts;
    const remaining: number[] = [];
    for (let i = 0; i < OTP_MAX_ATTEMPTS; i++) {
      const r = afterWrongCode(state, now);
      state = r.state;
      remaining.push(r.remaining);
    }
    expect(remaining).toEqual([2, 1, 0]);
    expect(lockedUntil(state, now)).toEqual(later(OTP_LOCK_MS));
    expect(lockedUntil(state, later(OTP_LOCK_MS - MINUTE))).not.toBeNull();
    expect(lockedUntil(state, later(OTP_LOCK_MS))).toBeNull();
  });

  it("after the wait the count starts again", () => {
    const locked = { failures: 0, lockedUntil: later(-MINUTE) };
    expect(afterWrongCode(locked, now)).toEqual({
      state: { failures: 1, lockedUntil: null },
      remaining: 2,
    });
  });
});
