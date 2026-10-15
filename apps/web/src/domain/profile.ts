// The person's poke profile (ADR 0020): what the profiler is told, when a profile is stale, and
// the recent slice the planner still gets in full.
import type { HistoryItem } from "./planner";
import { DAY } from "./time";
import type { PokeProfile } from "./types";

/** Closed tasks since the profile before it is written again. */
export const PROFILE_EVERY_TASKS = 10;
/** A profile older than this is rewritten once a task has closed since. */
export const PROFILE_MAX_AGE_MS = 7 * DAY;
/** How many closed tasks the profiler reads, and from how far back. */
export const PROFILE_TASKS = 30;
export const PROFILE_DAYS = 60;
/** The closed tasks the planner still gets in full, next to the profile. */
export const RECENT_TASKS = 8;
/** The profile's length limit, in words, as the prompt asks for it. */
export const PROFILE_MAX_WORDS = 120;

export interface ProfileInput {
  now: Date;
  timeZone: string;
  recipient: { name: string };
  /** The previous profile, or null for the first. */
  previous: string | null;
  /** The last closed tasks, newest first, in the planner's shape. */
  history: HistoryItem[];
}

/**
 * Whether the profile should be written again before planning: never with fewer than
 * PROFILE_EVERY_TASKS closed tasks, then after every PROFILE_EVERY_TASKS more, or when it is older
 * than PROFILE_MAX_AGE_MS and at least one task closed since.
 */
export function profileIsStale(
  profile: PokeProfile | null,
  closedTasks: number,
  now: Date,
): boolean {
  if (closedTasks < PROFILE_EVERY_TASKS) return false;
  if (profile === null) return true;
  const since = closedTasks - profile.tasksSeen;
  if (since >= PROFILE_EVERY_TASKS) return true;
  return since > 0 && now.getTime() - profile.writtenAt.getTime() > PROFILE_MAX_AGE_MS;
}
