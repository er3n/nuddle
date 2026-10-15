// The LLM poke planner (ADR 0017, docs/domain.md, Poke plan): what it is told, what it answers,
// and the pure rule check every answer passes before it is stored. Plan v0 (plan-pokes.ts) stays
// the first plan and the fallback.
import { humanChannelAllowed, type PlanPokesInput } from "./plan-pokes";
import { isQuiet } from "./quiet-hours";
import type { Reachability } from "./reachability";
import { MINUTE } from "./time";
import type {
  DeviceLevel,
  EdgeType,
  FinishMode,
  PokeLevel,
  PokeStep,
  QuietHours,
  Task,
  TaskEvent,
  TaskStatus,
  Timing,
} from "./types";

/** At most this many device steps in one plan. */
const MAX_DEVICE_STEPS = 8;
/** At least this long between two device steps. */
const MIN_STEP_GAP_MS = 3 * MINUTE;
/** The closed tasks the profiler reads (ADR 0020, open-questions#42); the planner gets RECENT_TASKS. */
export const HISTORY_TASKS = 30;
export const HISTORY_DAYS = 60;

/** One of the recipient's recent closed tasks, as the planner sees it. */
export interface HistoryItem {
  title: string;
  /** "self" or the sender's name. */
  from: string;
  /** What the sender is to the recipient; null for a task to self. */
  relationship: EdgeType | null;
  timing: Timing;
  /** The time the sender picked: the moment of an `at` task, the deadline of a `by` task. */
  time: Date;
  deadline: Date;
  outcome: TaskStatus;
  late: boolean;
  /** The pokes of the last plan that rang, with planned and actual time. */
  pokes: { level: PokeLevel; plannedAt: Date; firedAt: Date }[];
  /** When the recipient acted (done, photo sent, can't today), or null. */
  actedAt: Date | null;
  /**
   * The last poke that rang before the recipient acted: which one (1 = first that rang), its level
   * and how many seconds later they acted. Null when they never acted after a poke.
   */
  actedAfter: { poke: number; level: PokeLevel; secondsAfter: number } | null;
  snoozes: number;
  cantToday: boolean;
}

export interface PlannerInput {
  now: Date;
  /** The recipient's time zone; times are given in it. */
  timeZone: string;
  task: {
    rawText: string | null;
    title: string;
    detail: string | null;
    finishMode: FinishMode;
    timing: Timing;
    /** The picked time (moment or deadline). */
    time: Date;
    /** When the task becomes missed. */
    deadline: Date;
    /** Earliest allowed poke: now, or the end of a snooze. */
    notBefore: Date;
    /** Lowest device level allowed: after a snooze or re-request, the level already reached. */
    floor: DeviceLevel;
    snoozesSoFar: number;
  };
  sender: { name: string; self: boolean };
  recipient: { name: string };
  /** What the recipient is to the sender, and the sender to the recipient. Null for a task to self. */
  relationship: { recipientIsToSender: EdgeType; senderIsToRecipient: EdgeType | null } | null;
  /** The recipient's connections: what each is to the recipient. */
  recipientConnections: { name: string; relationship: EdgeType }[];
  quietHours: QuietHours;
  /** The highest level the recipient's phone can ring at now. */
  reachability: Reachability;
  /** Whether a HUMAN step (telling the sender) is allowed. */
  humanAllowed: boolean;
  /** How this person responds to pokes, from their whole history (ADR 0020); null until known. */
  profile: string | null;
  /** The last closed tasks in full, newest first (RECENT_TASKS of them). */
  history: HistoryItem[];
}

export interface PlanProposal {
  steps: { at: Date; level: PokeLevel }[];
  /** One short sentence for logs and evals; never shown to users. */
  reason: string;
}

const rank: Record<DeviceLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2 };

export interface CheckInput extends Pick<PlanPokesInput, "recipient" | "sender" | "senderEdge"> {
  task: Pick<Task, "deadline" | "senderId" | "recipientId" | "timing" | "remindAt">;
  notBefore: Date;
  floor: DeviceLevel;
}

/**
 * The rules every LLM plan must follow (docs/domain.md, Poke plan). A step that breaks one is
 * dropped; the result is null when no device step is left or only SILENT ones are, and then the
 * caller keeps plan v0.
 */
export function checkPlan(proposal: PlanProposal, input: CheckInput): PokeStep[] | null {
  const { task, recipient } = input;
  const earliest = Math.max(
    input.notBefore.getTime(),
    task.timing === "at" && task.remindAt ? task.remindAt.getTime() : 0,
  );
  const human = humanChannelAllowed(input);
  const kept: { at: Date; level: PokeLevel }[] = [];
  let devices = 0;
  let lastRank = rank[input.floor];
  let lastDeviceAt = -Infinity;
  let humanKept = false;

  const sorted = [...proposal.steps].sort((a, b) => a.at.getTime() - b.at.getTime());
  for (const step of sorted) {
    const at = step.at.getTime();
    if (Number.isNaN(at) || at < earliest || at >= task.deadline.getTime()) continue;
    if (isQuiet(step.at, recipient.quietHours)) continue;
    if (step.level === "HUMAN") {
      if (!human || humanKept) continue;
      kept.push(step);
      humanKept = true;
      continue;
    }
    // HUMAN is always the last step.
    if (humanKept) continue;
    if (rank[step.level] < lastRank) continue;
    if (at - lastDeviceAt < MIN_STEP_GAP_MS) continue;
    if (devices >= MAX_DEVICE_STEPS) continue;
    kept.push(step);
    devices++;
    lastRank = rank[step.level];
    lastDeviceAt = at;
  }

  const deviceSteps = kept.filter((s) => s.level !== "HUMAN");
  if (deviceSteps.length === 0 || deviceSteps.every((s) => s.level === "SILENT")) return null;
  return kept.map((s, i) => ({ seq: i + 1, plannedAt: s.at, level: s.level, firedAt: null }));
}

/** What the planner needs to know about one closed task, from its events and its last plan. */
export function historyItem(
  task: Task,
  events: readonly TaskEvent[],
  firedSteps: readonly { level: PokeLevel; plannedAt: Date; firedAt: Date }[],
  sender: { name: string; relationship: EdgeType | null },
): HistoryItem {
  const self = task.senderId === task.recipientId;
  const acted = events.find(
    (e) =>
      e.actorId === task.recipientId &&
      (e.type === "done" || e.type === "photo_sent" || e.type === "dropped"),
  );
  const pokes = [...firedSteps].sort((a, b) => a.firedAt.getTime() - b.firedAt.getTime());
  const actedAt = acted?.at ?? null;
  const before = actedAt ? pokes.filter((p) => p.firedAt.getTime() <= actedAt.getTime()) : [];
  const last = before.at(-1);
  return {
    title: task.title,
    from: self ? "self" : sender.name,
    relationship: self ? null : sender.relationship,
    timing: task.timing,
    time: task.remindAt ?? task.deadline,
    deadline: task.deadline,
    outcome: task.status,
    late: task.late,
    pokes,
    actedAt,
    actedAfter:
      actedAt && last
        ? {
            poke: before.length,
            level: last.level,
            secondsAfter: Math.round((actedAt.getTime() - last.firedAt.getTime()) / 1000),
          }
        : null,
    snoozes: events.filter((e) => e.type === "snoozed").length,
    cantToday: events.some((e) => e.type === "dropped"),
  };
}
