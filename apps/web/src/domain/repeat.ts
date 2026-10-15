// Repeating tasks (ADR 0021): a rule on every occurrence of a series, and the next occurrence's
// time from it. Pure; the time zone is the recipient's.
import { addDays, localDateOf, parseClockTime, zonedInstant, type LocalDate } from "./time";

type RepeatEvery = "day" | "week" | "weekdays";

export interface RepeatRule {
  every: RepeatEvery;
  /** ISO weekdays, 1 Monday to 7 Sunday; only for `week`. */
  days?: number[];
  /** Local time "HH:MM" of each occurrence. */
  time: string;
}

/** ISO weekday of a local date: 1 Monday to 7 Sunday. */
export function isoWeekday(d: LocalDate): number {
  const sunday0 = new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay();
  return sunday0 === 0 ? 7 : sunday0;
}

function matches(rule: RepeatRule, weekday: number): boolean {
  switch (rule.every) {
    case "day":
      return true;
    case "weekdays":
      return weekday <= 5;
    case "week":
      return (rule.days ?? []).includes(weekday);
  }
}

/**
 * Whether the sentence itself says the task comes back. The model sometimes invents a rule for a
 * school run or a bill said once; the server keeps a rule only when a word like "her gün", "her
 * salı", "hafta içi" or "her hafta" is in the text (ADR 0021).
 */
export function textSaysRepeat(text: string): boolean {
  const t = text.toLocaleLowerCase("tr");
  return /(^|\s)her(gün|\s)|hafta\s*içi|iş\s*gün|günlük|haftalık|her\s*sabah|her\s*akşam/.test(t);
}

/** A rule from the extractor or a command, or null when it makes no sense (no day, a bad time). */
export function repeatRuleOf(input: unknown): RepeatRule | null {
  if (typeof input !== "object" || input === null) return null;
  const r = input as { every?: unknown; days?: unknown; time?: unknown };
  if (r.every !== "day" && r.every !== "week" && r.every !== "weekdays") return null;
  if (typeof r.time !== "string" || !/^([01]\d|2[0-3]):[0-5]\d$/.test(r.time)) return null;
  if (r.every !== "week") return { every: r.every, time: r.time };
  const days = Array.isArray(r.days)
    ? [...new Set(r.days.filter((d): d is number => Number.isInteger(d) && d >= 1 && d <= 7))].sort(
        (a, b) => a - b,
      )
    : [];
  return days.length === 0 ? null : { every: "week", days, time: r.time };
}

/** The first occurrence strictly after `after`, in the recipient's time zone. */
export function nextOccurrenceAt(rule: RepeatRule, after: Date, timeZone: string): Date {
  const minutes = parseClockTime(rule.time);
  const start = localDateOf(after, timeZone);
  for (let i = 0; i <= 7; i++) {
    const date = addDays(start, i);
    if (!matches(rule, isoWeekday(date))) continue;
    const t = zonedInstant(date, minutes, timeZone);
    if (t.getTime() > after.getTime()) return t;
  }
  // A week rule always matches within 7 days; unreachable, kept for the type.
  return zonedInstant(addDays(start, 7), minutes, timeZone);
}
