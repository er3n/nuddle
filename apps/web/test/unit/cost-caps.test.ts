// Daily caps on LLM calls and sign-in codes (ADR 0024, open-questions#54).
import { describe, expect, it } from "vitest";
import {
  CAP_WINDOW_MS,
  capReachedUntil,
  capWindowStart,
  laterOf,
} from "../../src/domain/cost-caps";
import { HOUR } from "../../src/domain/time";

const now = new Date("2026-10-11T20:00:00+03:00");
const ago = (ms: number) => new Date(now.getTime() - ms);

describe("cost caps", () => {
  it("the window is the last 24 hours", () => {
    expect(capWindowStart(now)).toEqual(ago(24 * HOUR));
  });

  it("under the cap nothing waits", () => {
    expect(capReachedUntil({ count: 0, oldest: null }, 5, now)).toBeNull();
    expect(capReachedUntil({ count: 4, oldest: ago(HOUR) }, 5, now)).toBeNull();
  });

  it("at the cap the next use waits until the oldest leaves the window", () => {
    const oldest = ago(20 * HOUR);
    expect(capReachedUntil({ count: 5, oldest }, 5, now)).toEqual(
      new Date(oldest.getTime() + CAP_WINDOW_MS),
    );
    expect(capReachedUntil({ count: 7, oldest }, 5, now)).toEqual(ago(-4 * HOUR));
  });

  it("the later of two waits wins", () => {
    expect(laterOf(null, null)).toBeNull();
    expect(laterOf(ago(HOUR), null)).toEqual(ago(HOUR));
    expect(laterOf(null, ago(HOUR))).toEqual(ago(HOUR));
    expect(laterOf(ago(2 * HOUR), ago(HOUR))).toEqual(ago(HOUR));
  });
});
