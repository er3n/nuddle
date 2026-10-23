import { describe, expect, it } from "vitest";
import {
  isQuiet,
  latestQuietOverlap,
  nextQuietStart,
  quietIntervalAt,
  quietMomentOf,
} from "../../src/domain/quiet-hours";
import { toLocalIso, zonedInstant } from "../../src/domain/time";
import type { QuietHours } from "../../src/domain/types";

const istanbul: QuietHours = { start: "23:00", end: "07:00", timeZone: "Europe/Istanbul" };

describe("quiet hours", () => {
  it.each([
    ["2026-10-11T22:59:00+03:00", false],
    ["2026-10-11T23:00:00+03:00", true],
    ["2026-10-12T03:00:00+03:00", true],
    ["2026-10-12T06:59:00+03:00", true],
    ["2026-10-12T07:00:00+03:00", false],
    ["2026-10-12T12:00:00+03:00", false],
  ])("%s quiet: %s", (t, expected) => {
    expect(isQuiet(new Date(t), istanbul)).toBe(expected);
  });

  it("finds the next start of quiet hours, today or tomorrow (NUD-163)", () => {
    expect(nextQuietStart(new Date("2026-10-11T22:50:00+03:00"), istanbul)).toEqual(
      new Date("2026-10-11T23:00:00+03:00"),
    );
    expect(nextQuietStart(new Date("2026-10-12T02:00:00+03:00"), istanbul)).toEqual(
      new Date("2026-10-12T23:00:00+03:00"),
    );
    expect(
      nextQuietStart(new Date("2026-10-12T02:00:00+03:00"), { ...istanbul, end: "23:00" }),
    ).toBeNull();
  });

  it("returns the interval spanning midnight", () => {
    expect(quietIntervalAt(new Date("2026-10-12T02:00:00+03:00"), istanbul)).toEqual({
      start: new Date("2026-10-11T23:00:00+03:00"),
      end: new Date("2026-10-12T07:00:00+03:00"),
    });
  });

  it("supports a window inside one day", () => {
    const nap: QuietHours = { start: "13:00", end: "15:00", timeZone: "Europe/Istanbul" };
    expect(isQuiet(new Date("2026-10-12T14:00:00+03:00"), nap)).toBe(true);
    expect(isQuiet(new Date("2026-10-12T16:00:00+03:00"), nap)).toBe(false);
  });

  it("treats equal start and end as no quiet hours", () => {
    const none: QuietHours = { start: "00:00", end: "00:00", timeZone: "Europe/Istanbul" };
    expect(isQuiet(new Date("2026-10-12T03:00:00+03:00"), none)).toBe(false);
  });

  it("finds the quiet interval overlapping a range", () => {
    expect(
      latestQuietOverlap(
        new Date("2026-10-12T06:30:00+03:00"),
        new Date("2026-10-12T07:30:00+03:00"),
        istanbul,
      ),
    ).toEqual({
      start: new Date("2026-10-11T23:00:00+03:00"),
      end: new Date("2026-10-12T07:00:00+03:00"),
    });
    expect(
      latestQuietOverlap(
        new Date("2026-10-12T08:00:00+03:00"),
        new Date("2026-10-12T09:00:00+03:00"),
        istanbul,
      ),
    ).toBeNull();
  });

  it("handles a DST change in the recipient's zone", () => {
    // Europe/Berlin leaves summer time on 2026-10-25 at 03:00 local.
    const berlin: QuietHours = { start: "23:00", end: "07:00", timeZone: "Europe/Berlin" };
    expect(quietIntervalAt(new Date("2026-10-25T05:00:00Z"), berlin)).toEqual({
      start: new Date("2026-10-24T21:00:00Z"),
      end: new Date("2026-10-25T06:00:00Z"),
    });
  });
});

describe("time zone helpers", () => {
  it("finds the instant for a local time", () => {
    expect(zonedInstant({ year: 2026, month: 10, day: 12 }, 9 * 60, "Europe/Istanbul")).toEqual(
      new Date("2026-10-12T06:00:00Z"),
    );
  });

  it("formats local ISO with offset", () => {
    expect(toLocalIso(new Date("2026-10-11T17:00:00Z"), "Europe/Istanbul")).toBe(
      "2026-10-11T20:00:00+03:00",
    );
    expect(toLocalIso(new Date("2026-01-15T12:00:00Z"), "America/New_York")).toBe(
      "2026-01-15T07:00:00-05:00",
    );
  });
});

describe("a moment inside quiet hours (NUD-120)", () => {
  const eren: QuietHours = { start: "23:00", end: "06:00", timeZone: "Europe/Istanbul" };
  const at = (moment: string, leadMinutes: number | null = null) =>
    quietMomentOf({ timing: "at", moment: new Date(moment), leadMinutes }, eren);

  it("a 05:00 wake-up is quiet and 06:00 is suggested", () => {
    expect(at("2026-10-21T05:00:00+03:00")).toEqual({
      interval: {
        start: new Date("2026-10-20T23:00:00+03:00"),
        end: new Date("2026-10-21T06:00:00+03:00"),
      },
      suggested: new Date("2026-10-21T06:00:00+03:00"),
    });
  });

  it("the moment at the end of quiet hours is free", () => {
    expect(at("2026-10-21T06:00:00+03:00")).toBeNull();
  });

  it("a lead that starts inside quiet hours counts, and the suggestion keeps the lead", () => {
    expect(at("2026-10-21T06:10:00+03:00", 20)?.suggested).toEqual(
      new Date("2026-10-21T06:20:00+03:00"),
    );
    expect(at("2026-10-21T06:30:00+03:00", 20)).toBeNull();
  });

  it("a `by` time minutes away is written as a moment, so inside quiet hours it is asked about (NUD-171)", () => {
    const task = {
      timing: "by" as const,
      moment: new Date("2026-10-21T00:08:00+03:00"),
      leadMinutes: null,
    };
    expect(quietMomentOf(task, eren, new Date("2026-10-21T00:00:00+03:00"))?.suggested).toEqual(
      new Date("2026-10-21T06:00:00+03:00"),
    );
    expect(quietMomentOf(task, eren, new Date("2026-10-20T23:30:00+03:00"))).toBeNull();
  });

  it("a `by` deadline is never asked about: its pokes move before quiet hours", () => {
    expect(
      quietMomentOf(
        { timing: "by", moment: new Date("2026-10-21T05:00:00+03:00"), leadMinutes: null },
        eren,
      ),
    ).toBeNull();
  });
});
