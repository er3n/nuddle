// planPokes v0 (docs/domain.md, Poke plan; open-questions#18). A pure function from the task, the
// recipient and `now` to an ordered list of steps. Documented in docs/domain.md, "Poke plan v0".
//
// The heuristic:
// 1. The window ends at the deadline, or at the start of quiet hours if the deadline falls inside
//    them, so the last poke lands before quiet hours begin.
// 2. The window is the last 60 minutes before that end. It never starts before `now`, and if it
//    overlaps quiet hours it starts when they end. If less than 2 minutes would remain after
//    quiet hours end (a deadline at 07:00), the window moves before quiet hours instead.
// 3. Ladder, at nominal offsets before the end: SILENT -60 min, SOUND -30, FULL_SCREEN -15, then
//    HUMAN -5. When the window is shorter than 60 minutes every offset shrinks proportionally.
// 4. `floor` drops the lower rungs: after a re-request poking resumes at the level it had reached.
// 5. HUMAN is planned only when another person can be told: the task is not to self and both
//    human channel switches allow it. It is the last step; the server fires it through a
//    `human_check` timer only if the task is still open.
// 6. A window shorter than 2 minutes gets a single SOUND step and no HUMAN step: SILENT is never
//    the only step.
// 7. Reachability does not remove steps. A level the device cannot reach stays in the plan and
//    degrades on the device ("permission needed").
// 8. An `at` task (ADR 0017) starts at its moment instead of ending at the deadline: SOUND at the
//    moment, FULL_SCREEN 5 and 20 minutes after it, HUMAN 30 minutes after it when allowed. Steps
//    at or after the deadline or inside quiet hours are dropped. A snooze or re-request starts the
//    same ladder where poking resumes, at least at the level already reached.
// `history` is accepted for callers; v0 ignores it. The LLM planner reads it (ADR 0017).
import { latestQuietOverlap, quietIntervalAt } from "./quiet-hours";
import { pokeStartOf } from "./timing";
import type { Reachability } from "./reachability";
import { MINUTE } from "./time";
import type { DeviceLevel, Edge, Person, PokeLevel, PokeStep, Task, TaskEvent } from "./types";

const POKE_WINDOW_MS = 60 * MINUTE;
const MIN_WINDOW_MS = 2 * MINUTE;
const HUMAN_OFFSET_MS = 5 * MINUTE;
/** A one-time message due within this long goes out at once (NUD-79): "yemek hazır" does not wait. */
const NOTICE_NOW_MS = 5 * MINUTE;

const ladder: readonly { level: DeviceLevel; offsetMs: number }[] = [
  { level: "SILENT", offsetMs: 60 * MINUTE },
  { level: "SOUND", offsetMs: 30 * MINUTE },
  { level: "FULL_SCREEN", offsetMs: 15 * MINUTE },
];

const deviceRank: Record<DeviceLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2 };

/** TODO(open-questions#43): the `at` ladder, as offsets after the moment. */
const atLadder: readonly { level: DeviceLevel; afterMs: number }[] = [
  { level: "SOUND", afterMs: 0 },
  { level: "FULL_SCREEN", afterMs: 5 * MINUTE },
  { level: "FULL_SCREEN", afterMs: 20 * MINUTE },
];
const AT_HUMAN_AFTER_MS = 30 * MINUTE;

export interface PlanPokesInput {
  task: Pick<Task, "deadline" | "senderId" | "recipientId"> &
    Partial<Pick<Task, "timing" | "remindAt" | "leadMinutes" | "pokeFrom" | "finishMode">>;
  recipient: Pick<Person, "quietHours" | "useConnectionsForMyTasks">;
  /** The human channel in the MVP is the sender (docs/product.md, Human channel). */
  sender: Pick<Person, "notifyMeForOthersTasks">;
  /** The sender's edge to the recipient; its `notifyMe` narrows the sender's switch (5b). */
  senderEdge?: Pick<Edge, "notifyMe"> | null;
  reachability: Reachability;
  history: readonly TaskEvent[];
  now: Date;
  /** Lowest device level to use. Defaults to SILENT. */
  floor?: DeviceLevel;
}

/** The end of the poke window: the deadline, or the start of quiet hours containing it. */
export function pokeWindowEnd(deadline: Date, recipient: Pick<Person, "quietHours">): Date {
  return quietIntervalAt(deadline, recipient.quietHours)?.start ?? deadline;
}

/** The poke window [start, end), or null when no time is left outside quiet hours. */
function pokeWindow(
  deadline: Date,
  recipient: Pick<Person, "quietHours">,
  now: Date,
  /** The recipient's earliest poke time (NUD-78): the window never starts before it. */
  pokeFrom: Date | null = null,
): { start: Date; end: Date } | null {
  let end = pokeWindowEnd(deadline, recipient);
  const floor = Math.max(now.getTime(), pokeFrom?.getTime() ?? 0);
  // Each pass either settles the window or moves its end to an earlier quiet start.
  for (let pass = 0; pass < 3; pass++) {
    if (end.getTime() <= floor) return null;
    const start = new Date(Math.max(floor, end.getTime() - POKE_WINDOW_MS));
    const quiet = latestQuietOverlap(start, end, recipient.quietHours);
    if (!quiet) return { start, end };
    if (end.getTime() - quiet.end.getTime() >= MIN_WINDOW_MS) return { start: quiet.end, end };
    end = quiet.start;
  }
  return null;
}

/** Whether the sender may be told: rule 5. */
export function humanChannelAllowed(
  input: Pick<PlanPokesInput, "task" | "recipient" | "sender" | "senderEdge">,
): boolean {
  const { task, recipient, sender } = input;
  return (
    task.senderId !== task.recipientId &&
    recipient.useConnectionsForMyTasks &&
    sender.notifyMeForOthersTasks &&
    input.senderEdge?.notifyMe !== false
  );
}

/**
 * Rule 8: an `at` task. The ladder starts at the moment minus the lead (NUD-70). The sender asked
 * for that time, so quiet hours do not hold inside the task's own window. HUMAN comes 30 minutes
 * after the start, or 5 minutes before the deadline when that is sooner.
 */
function planAt(input: PlanPokesInput, remindAt: Date): PokeStep[] {
  const { task, now } = input;
  const start = pokeStartOf({ remindAt, leadMinutes: task.leadMinutes ?? 0 }) ?? remindAt;
  const anchor = Math.max(start.getTime(), now.getTime());
  const deadline = task.deadline.getTime();
  const floorRank = deviceRank[input.floor ?? "SILENT"];
  const planned: { level: PokeLevel; at: number }[] = atLadder.map((rung) => ({
    level: deviceRank[rung.level] >= floorRank ? rung.level : (input.floor ?? rung.level),
    at: anchor + rung.afterMs,
  }));
  const device = planned.filter((p) => p.at < deadline);
  if (humanChannelAllowed(input)) {
    const human = Math.min(anchor + AT_HUMAN_AFTER_MS, deadline - HUMAN_OFFSET_MS);
    const lastDevice = device.at(-1)?.at ?? -Infinity;
    if (human > lastDevice && human < deadline) device.push({ level: "HUMAN", at: human });
  }
  const allowed = device;
  if (!allowed.some((p) => p.level !== "HUMAN")) return [];
  return allowed.map((p, i) => ({
    seq: i + 1,
    plannedAt: new Date(p.at),
    level: p.level,
    firedAt: null,
  }));
}

export function planPokes(input: PlanPokesInput): PokeStep[] {
  const { task, recipient, now } = input;
  // A one-time message (NUD-79): delivered once at its time, no escalation, no HUMAN.
  if (task.finishMode === "notice") {
    const due = (task.remindAt ?? task.deadline).getTime();
    const at = new Date(due - now.getTime() <= NOTICE_NOW_MS ? now.getTime() : due);
    return at.getTime() < task.deadline.getTime()
      ? [{ seq: 1, plannedAt: at, level: "SOUND", firedAt: null }]
      : [];
  }
  if (task.timing === "at" && task.remindAt) return planAt(input, task.remindAt);
  const window = pokeWindow(task.deadline, recipient, now, task.pokeFrom ?? null);
  if (!window) return [];
  const { start, end } = window;

  const span = end.getTime() - start.getTime();
  if (span < MIN_WINDOW_MS) {
    return [{ seq: 1, plannedAt: start, level: "SOUND", firedAt: null }];
  }

  const scale = span / POKE_WINDOW_MS;
  const floorRank = deviceRank[input.floor ?? "SILENT"];
  const planned: { level: PokeLevel; at: number }[] = ladder
    .filter((rung) => deviceRank[rung.level] >= floorRank)
    .map((rung) => ({ level: rung.level, at: end.getTime() - rung.offsetMs * scale }));

  if (humanChannelAllowed(input))
    planned.push({ level: "HUMAN", at: end.getTime() - HUMAN_OFFSET_MS * scale });

  return planned.map((p, i) => ({
    seq: i + 1,
    plannedAt: new Date(Math.round(p.at)),
    level: p.level,
    firedAt: null,
  }));
}
