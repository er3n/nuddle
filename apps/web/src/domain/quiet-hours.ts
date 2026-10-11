// Quiet hours (docs/domain.md, Time rules): no channel fires inside the window, full screen
// included. Stored as local times in the recipient's time zone.
import { addDays, localDateOf, parseClockTime, zonedInstant } from "./time";
import type { QuietHours } from "./types";

export interface Interval {
  start: Date;
  end: Date;
}

/** The quiet intervals that start on the local day before, of, and after `t`. */
function intervalsAround(t: Date, qh: QuietHours): Interval[] {
  const start = parseClockTime(qh.start);
  const end = parseClockTime(qh.end);
  if (start === end) return [];
  const today = localDateOf(t, qh.timeZone);
  return [-1, 0, 1].map((offset) => {
    const day = addDays(today, offset);
    return {
      start: zonedInstant(day, start, qh.timeZone),
      // A window like 23:00-07:00 ends on the next local day.
      end: zonedInstant(end > start ? day : addDays(day, 1), end, qh.timeZone),
    };
  });
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
