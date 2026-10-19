// Task timing (ADR 0017, docs/domain.md): a `by` task is done by its deadline; an `at` task is a
// moment, and its deadline (when it becomes missed) is the moment plus a grace.
import { HOUR, MINUTE, addMs } from "./time";
import type { Task, Timing } from "./types";

/** TODO(open-questions#43): 60 minutes when the extractor gives none, kept between 15 minutes and 12 hours. */
export const DEFAULT_MISS_AFTER_MS = 60 * MINUTE;
const MIN_MISS_AFTER_MS = 15 * MINUTE;
const MAX_MISS_AFTER_MS = 12 * HOUR;
/** A flight or an intercity trip needs the most; more than this is a different task. */
const MAX_LEAD_MINUTES = 6 * 60;

/** The grace of an `at` task from the extractor's `missAfterMinutes`, kept inside the limits. */
export function missAfterMs(minutes: number | null | undefined): number {
  if (minutes === null || minutes === undefined || !Number.isFinite(minutes)) {
    return DEFAULT_MISS_AFTER_MS;
  }
  return Math.min(MAX_MISS_AFTER_MS, Math.max(MIN_MISS_AFTER_MS, Math.round(minutes) * MINUTE));
}

/** Deadline and moment from the time the person picked. */
export function scheduleOf(
  timing: Timing,
  time: Date,
  missAfterMinutes?: number | null,
): Pick<Task, "deadline" | "remindAt"> {
  return timing === "at"
    ? { deadline: addMs(time, missAfterMs(missAfterMinutes)), remindAt: time }
    : { deadline: time, remindAt: null };
}

/** The lead of an `at` task from the extractor's `leadMinutes`, kept inside the limits; 0 for `by`. */
export function leadMinutesOf(timing: Timing, minutes: number | null | undefined): number {
  if (timing !== "at" || minutes === null || minutes === undefined || !Number.isFinite(minutes)) {
    return 0;
  }
  return Math.min(MAX_LEAD_MINUTES, Math.max(0, Math.round(minutes)));
}

/**
 * When poking may start: for an `at` task the moment minus its lead; for a `by` task the start the
 * recipient set (NUD-78), else null (the plan decides).
 */
export function pokeStartOf(
  task: Pick<Task, "remindAt" | "leadMinutes"> & Partial<Pick<Task, "pokeFrom">>,
): Date | null {
  if (task.remindAt !== null) return addMs(task.remindAt, -task.leadMinutes * MINUTE);
  return task.pokeFrom ?? null;
}

export { MAX_LEAD_MINUTES };

/** The time the person picked and sees: the moment of an `at` task, the deadline of a `by` task. */
export function pickedTime(task: Pick<Task, "deadline" | "remindAt">): Date {
  return task.remindAt ?? task.deadline;
}

/**
 * TODO(open-questions#55): a `by` time picked less than this far ahead is a moment (NUD-116). A deadline a
 * minute away left one poke and a miss seconds later; as a moment it rings the `at` ladder and is
 * missed only after the grace.
 */
const NEAR_BY_MS = 10 * MINUTE;

/** The timing a picked time gets: `by` turns into `at` when the time is less than NEAR_BY_MS away. */
export function timingFor(timing: Timing, time: Date, now: Date): Timing {
  return timing === "by" && time.getTime() - now.getTime() < NEAR_BY_MS ? "at" : timing;
}

/**
 * A new time picked for an existing task (edit, new deadline): as `reschedule`, except that a `by`
 * time less than NEAR_BY_MS away makes the task a moment with the default grace (NUD-116).
 */
export function retime(
  task: Pick<Task, "timing" | "deadline" | "remindAt">,
  time: Date,
  now: Date,
): Pick<Task, "timing" | "deadline" | "remindAt"> {
  const timing = timingFor(task.timing, time, now);
  if (timing === task.timing) return { timing, ...reschedule(task, time) };
  return { timing, ...scheduleOf(timing, time, null) };
}

/** Move a task to a newly picked time; an `at` task keeps its grace. */
export function reschedule(
  task: Pick<Task, "timing" | "deadline" | "remindAt">,
  time: Date,
): Pick<Task, "deadline" | "remindAt"> {
  if (task.timing === "by" || task.remindAt === null) return { deadline: time, remindAt: null };
  return {
    deadline: addMs(time, task.deadline.getTime() - task.remindAt.getTime()),
    remindAt: time,
  };
}
