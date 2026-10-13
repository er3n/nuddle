// Task timing (ADR 0017, docs/domain.md): a `by` task is done by its deadline; an `at` task is a
// moment, and its deadline (when it becomes missed) is the moment plus a grace.
import { HOUR, MINUTE, addMs } from "./time";
import type { Task, Timing } from "./types";

/** TODO(open-questions#43): 60 minutes when the extractor gives none, kept between 15 minutes and 12 hours. */
export const DEFAULT_MISS_AFTER_MS = 60 * MINUTE;
const MIN_MISS_AFTER_MS = 15 * MINUTE;
const MAX_MISS_AFTER_MS = 12 * HOUR;

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

/** The time the person picked and sees: the moment of an `at` task, the deadline of a `by` task. */
export function pickedTime(task: Pick<Task, "deadline" | "remindAt">): Date {
  return task.remindAt ?? task.deadline;
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
