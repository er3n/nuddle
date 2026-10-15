// Repeating tasks (ADR 0021): the next occurrence from a rule, and what counts as a rule.
import { describe, expect, it } from "vitest";
import {
  isoWeekday,
  nextOccurrenceAt,
  repeatRuleOf,
  textSaysRepeat,
} from "../../src/domain/repeat";

const TZ = "Europe/Istanbul";
// Wednesday 2026-10-14 10:00 Istanbul.
const wednesday = new Date("2026-10-14T10:00:00+03:00");

describe("nextOccurrenceAt", () => {
  it("every day: today when the time is still ahead, else tomorrow", () => {
    expect(nextOccurrenceAt({ every: "day", time: "19:00" }, wednesday, TZ)).toEqual(
      new Date("2026-10-14T19:00:00+03:00"),
    );
    expect(nextOccurrenceAt({ every: "day", time: "09:00" }, wednesday, TZ)).toEqual(
      new Date("2026-10-15T09:00:00+03:00"),
    );
    // Exactly the time counts as passed: strictly after.
    expect(
      nextOccurrenceAt({ every: "day", time: "10:00" }, new Date("2026-10-14T10:00:00+03:00"), TZ),
    ).toEqual(new Date("2026-10-15T10:00:00+03:00"));
  });

  it("given weekdays: the next listed day, wrapping over the weekend", () => {
    const tuesday = { every: "week" as const, days: [2], time: "18:00" };
    expect(nextOccurrenceAt(tuesday, wednesday, TZ)).toEqual(new Date("2026-10-20T18:00:00+03:00"));
    const monThu = { every: "week" as const, days: [1, 4], time: "07:30" };
    expect(nextOccurrenceAt(monThu, wednesday, TZ)).toEqual(new Date("2026-10-15T07:30:00+03:00"));
    expect(nextOccurrenceAt(monThu, new Date("2026-10-15T07:30:00+03:00"), TZ)).toEqual(
      new Date("2026-10-19T07:30:00+03:00"),
    );
  });

  it("weekdays: Friday evening goes to Monday", () => {
    const friday = new Date("2026-10-16T20:00:00+03:00");
    expect(nextOccurrenceAt({ every: "weekdays", time: "07:00" }, friday, TZ)).toEqual(
      new Date("2026-10-19T07:00:00+03:00"),
    );
  });

  it("the ISO weekday of a local date", () => {
    expect(isoWeekday({ year: 2026, month: 10, day: 14 })).toBe(3);
    expect(isoWeekday({ year: 2026, month: 10, day: 18 })).toBe(7);
    expect(isoWeekday({ year: 2026, month: 10, day: 19 })).toBe(1);
  });
});

describe("textSaysRepeat", () => {
  it("is true only for a sentence that says the task comes back", () => {
    for (const t of [
      "annem her gün 9da ilacını alsın",
      "eren her salı çöpü çıkarsın",
      "bahar hafta içi her sabah 7de kalksın",
      "Her hafta cuma temizlik",
      "hergün su iç",
    ]) {
      expect(textSaysRepeat(t), t).toBe(true);
    }
    for (const t of [
      "defne cuma akşama kadar ödevini bitirsin",
      "eren pazartesi sabah 250 tl elektrik faturasını ödesin",
      "eren yarın 14.30da diş randevusu var",
      "herkese haber ver",
      "sibel herhalde gelmez",
    ]) {
      expect(textSaysRepeat(t), t).toBe(false);
    }
  });
});

describe("repeatRuleOf", () => {
  it("keeps a sound rule, cleans the days, refuses the rest", () => {
    expect(repeatRuleOf({ every: "day", days: null, time: "09:00" })).toEqual({
      every: "day",
      time: "09:00",
    });
    expect(repeatRuleOf({ every: "week", days: [4, 2, 2, 9, 0], time: "18:00" })).toEqual({
      every: "week",
      days: [2, 4],
      time: "18:00",
    });
    expect(repeatRuleOf({ every: "week", days: [], time: "18:00" })).toBeNull();
    expect(repeatRuleOf({ every: "day", days: null, time: "9:00" })).toBeNull();
    expect(repeatRuleOf({ every: "hour", days: null, time: "09:00" })).toBeNull();
    expect(repeatRuleOf(null)).toBeNull();
    expect(repeatRuleOf(undefined)).toBeNull();
  });
});
