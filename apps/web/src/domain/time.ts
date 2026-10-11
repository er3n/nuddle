// Time zone arithmetic on instants, using only Intl (no I/O, no wall clock).

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

export function addMs(t: Date, ms: number): Date {
  return new Date(t.getTime() + ms);
}

export interface LocalDate {
  year: number;
  month: number;
  day: number;
}

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(timeZone: string): Intl.DateTimeFormat {
  let f = formatters.get(timeZone);
  if (!f) {
    f = new Intl.DateTimeFormat("en-US", {
      timeZone,
      hourCycle: "h23",
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "numeric",
      second: "numeric",
    });
    formatters.set(timeZone, f);
  }
  return f;
}

function localParts(t: Date, timeZone: string): Record<string, number> {
  const parts: Record<string, number> = {};
  for (const p of formatter(timeZone).formatToParts(t)) {
    if (p.type !== "literal") parts[p.type] = Number(p.value);
  }
  return parts;
}

/** Offset of `timeZone` from UTC at instant `t`, in milliseconds. */
function offsetMs(t: Date, timeZone: string): number {
  const p = localParts(t, timeZone);
  const asUtc = Date.UTC(
    p.year ?? 0,
    (p.month ?? 1) - 1,
    p.day ?? 1,
    p.hour ?? 0,
    p.minute ?? 0,
    p.second ?? 0,
  );
  return asUtc - Math.floor(t.getTime() / 1000) * 1000;
}

export function localDateOf(t: Date, timeZone: string): LocalDate {
  const p = localParts(t, timeZone);
  return { year: p.year ?? 0, month: p.month ?? 1, day: p.day ?? 1 };
}

export function addDays(d: LocalDate, days: number): LocalDate {
  const u = new Date(Date.UTC(d.year, d.month - 1, d.day + days));
  return { year: u.getUTCFullYear(), month: u.getUTCMonth() + 1, day: u.getUTCDate() };
}

/** "HH:MM" to minutes after midnight. */
export function parseClockTime(hhmm: string): number {
  const match = /^(\d{2}):(\d{2})$/.exec(hhmm);
  if (!match) throw new Error(`invalid clock time: ${hhmm}`);
  return Number(match[1]) * 60 + Number(match[2]);
}

/**
 * The instant at which the local clock in `timeZone` shows `minutes` after midnight on `date`.
 * In a DST gap the result lands just after the gap.
 */
export function zonedInstant(date: LocalDate, minutes: number, timeZone: string): Date {
  const naive = Date.UTC(date.year, date.month - 1, date.day, 0, minutes);
  const first = naive - offsetMs(new Date(naive), timeZone);
  const second = naive - offsetMs(new Date(first), timeZone);
  return new Date(second);
}

/** `t` as local ISO 8601 with the zone's offset, e.g. "2026-10-11T20:00:00+03:00". */
export function toLocalIso(t: Date, timeZone: string): string {
  const p = localParts(t, timeZone);
  const pad = (n: number | undefined, w = 2): string => String(n ?? 0).padStart(w, "0");
  const offsetMinutes = Math.round(offsetMs(t, timeZone) / MINUTE);
  const sign = offsetMinutes < 0 ? "-" : "+";
  const abs = Math.abs(offsetMinutes);
  return (
    `${pad(p.year, 4)}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}` +
    `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  );
}
