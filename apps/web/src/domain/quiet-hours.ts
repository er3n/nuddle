// Quiet hours (docs/domain.md, Time rules): no channel fires inside the window, full screen
// included. Stored as local times in the recipient's time zone.
import {
  addDays,
  addMs,
  type LocalDate,
  localDateOf,
  MINUTE,
  parseClockTime,
  zonedInstant,
} from "./time";
import { leadMinutesOf } from "./timing";
import type { QuietHours, Timing } from "./types";

export interface Interval {
  start: Date;
  end: Date;
}

// The quiet interval that starts on a given local day, as epoch milliseconds. It depends only on
// the quiet hours and the day, and each one costs several Intl calls, so it is computed once.
const dayIntervals = new Map<string, { start: number; end: number }>();
const MAX_CACHED_DAYS = 10_000;

function intervalStartingOn(day: LocalDate, start: number, end: number, qh: QuietHours): Interval {
  const key = `${qh.timeZone}|${qh.start}|${qh.end}|${String(day.year)}-${String(day.month)}-${String(day.day)}`;
  let cached = dayIntervals.get(key);
  if (!cached) {
    if (dayIntervals.size >= MAX_CACHED_DAYS) dayIntervals.clear();
    cached = {
      start: zonedInstant(day, start, qh.timeZone).getTime(),
      // A window like 23:00-07:00 ends on the next local day.
      end: zonedInstant(end > start ? day : addDays(day, 1), end, qh.timeZone).getTime(),
    };
    dayIntervals.set(key, cached);
  }
  return { start: new Date(cached.start), end: new Date(cached.end) };
}

/** The quiet intervals that start on the local day before, of, and after `t`. */
function intervalsAround(t: Date, qh: QuietHours): Interval[] {
  const start = parseClockTime(qh.start);
  const end = parseClockTime(qh.end);
  if (start === end) return [];
  const today = localDateOf(t, qh.timeZone);
  return [-1, 0, 1].map((offset) => intervalStartingOn(addDays(today, offset), start, end, qh));
}

/** The quiet interval containing `t` (start inclusive, end exclusive), if any. */
export function quietIntervalAt(t: Date, qh: QuietHours): Interval | null {
  for (const i of intervalsAround(t, qh)) {
    if (i.start.getTime() <= t.getTime() && t.getTime() < i.end.getTime()) return i;
  }
  return null;
}

export function isQuiet(t: Date, qh: QuietHours): boolean {
  return quietIntervalAt(t, qh) !== null;
}

/** The quiet interval overlapping [from, to) that ends last, or null if the range is free. */
export function latestQuietOverlap(from: Date, to: Date, qh: QuietHours): Interval | null {
  let latest: Interval | null = null;
  for (const i of [...intervalsAround(from, qh), ...intervalsAround(to, qh)]) {
    const overlaps = i.start.getTime() < to.getTime() && i.end.getTime() > from.getTime();
    if (overlaps && (latest === null || i.end.getTime() > latest.end.getTime())) latest = i;
  }
  return latest;
}

/**
 * A moment written as text whose pokes would start inside the recipient's quiet hours (NUD-120,
 * open-questions#47): its start (the moment minus the lead) up to the moment overlaps them. The
 * preview then asks before the task exists; `suggested` is the moment that starts poking as quiet
 * hours end. Null for a `by` task or a free moment.
 */
export function quietMomentOf(
  task: { timing: Timing; moment: Date; leadMinutes: number | null },
  qh: QuietHours,
): { interval: Interval; suggested: Date } | null {
  if (task.timing !== "at") return null;
  const leadMs = leadMinutesOf("at", task.leadMinutes) * MINUTE;
  const start = addMs(task.moment, -leadMs);
  const interval = latestQuietOverlap(start, addMs(task.moment, 1), qh);
  return interval ? { interval, suggested: addMs(interval.end, leadMs) } : null;
}
