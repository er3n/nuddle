import { describe, expect, it } from "vitest";
import {
  DEFAULT_MISS_AFTER_MS,
  leadMinutesOf,
  pokeStartOf,
  missAfterMs,
  pickedTime,
  reschedule,
  scheduleOf,
} from "../../src/domain/timing";
import { HOUR, MINUTE } from "../../src/domain/time";

const moment = new Date("2026-10-13T15:42:00Z");

describe("task timing (ADR 0017)", () => {
  it("a by task's deadline is the picked time", () => {
    expect(scheduleOf("by", moment, 30)).toEqual({ deadline: moment, remindAt: null });
  });

  it("an at task's deadline is the moment plus its grace", () => {
    expect(scheduleOf("at", moment, 30)).toEqual({
      deadline: new Date("2026-10-13T16:12:00Z"),
      remindAt: moment,
    });
  });

  it("keeps the grace between 15 minutes and 12 hours, 60 minutes when missing", () => {
    expect(missAfterMs(null)).toBe(DEFAULT_MISS_AFTER_MS);
    expect(missAfterMs(undefined)).toBe(60 * MINUTE);
    expect(missAfterMs(5)).toBe(15 * MINUTE);
    expect(missAfterMs(2000)).toBe(12 * HOUR);
    expect(missAfterMs(120)).toBe(2 * HOUR);
  });

  it("moving an at task keeps its grace; the picked time is the moment", () => {
    const task = { timing: "at" as const, ...scheduleOf("at", moment, 30) };
    const later = new Date("2026-10-13T17:00:00Z");
    const moved = reschedule(task, later);
    expect(moved).toEqual({ deadline: new Date("2026-10-13T17:30:00Z"), remindAt: later });
    expect(pickedTime({ ...task, ...moved })).toEqual(later);
    expect(reschedule({ timing: "by", deadline: moment, remindAt: null }, later)).toEqual({
      deadline: later,
      remindAt: null,
    });
  });
});

describe("lead (NUD-70)", () => {
  it("is clamped to 0..6 hours and ignored for by tasks", () => {
    expect(leadMinutesOf("at", 45)).toBe(45);
    expect(leadMinutesOf("at", -5)).toBe(0);
    expect(leadMinutesOf("at", 1000)).toBe(360);
    expect(leadMinutesOf("at", null)).toBe(0);
    expect(leadMinutesOf("by", 45)).toBe(0);
  });

  it("moves the poke start before the moment", () => {
    const moment = new Date("2026-10-14T17:30:00+03:00");
    expect(pokeStartOf({ remindAt: moment, leadMinutes: 20 })).toEqual(
      new Date("2026-10-14T17:10:00+03:00"),
    );
    expect(pokeStartOf({ remindAt: null, leadMinutes: 20 })).toBeNull();
  });
});
