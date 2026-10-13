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
import type { Reachability } from "./reachability";
import { MINUTE } from "./time";
import type { DeviceLevel, Edge, Person, PokeLevel, PokeStep, Task, TaskEvent } from "./types";

const POKE_WINDOW_MS = 60 * MINUTE;
const MIN_WINDOW_MS = 2 * MINUTE;
const HUMAN_OFFSET_MS = 5 * MINUTE;

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
    Partial<Pick<Task, "timing" | "remindAt">>;
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
): { start: Date; end: Date } | null {
  let end = pokeWindowEnd(deadline, recipient);
  // Each pass either settles the window or moves its end to an earlier quiet start.
  for (let pass = 0; pass < 3; pass++) {
    if (end.getTime() <= now.getTime()) return null;
    const start = new Date(Math.max(now.getTime(), end.getTime() - POKE_WINDOW_MS));
    const quiet = latestQuietOverlap(start, end, recipient.quietHours);
    if (!quiet) return { start, end };
    if (end.getTime() - quiet.end.getTime() >= MIN_WINDOW_MS) return { start: quiet.end, end };
    end = quiet.start;
  }
  return null;
}

/** Whether the sender may be told: rule 5. */
function humanChannelAllowed(input: PlanPokesInput): boolean {
  const { task, recipient, sender } = input;
  return (
    task.senderId !== task.recipientId &&
    recipient.useConnectionsForMyTasks &&
    sender.notifyMeForOthersTasks &&
    input.senderEdge?.notifyMe !== false
  );
}

/** Rule 8: an `at` task. */
function planAt(input: PlanPokesInput, remindAt: Date): PokeStep[] {
  const { task, recipient, now } = input;
  const anchor = Math.max(remindAt.getTime(), now.getTime());
  const floorRank = deviceRank[input.floor ?? "SILENT"];
  const planned: { level: PokeLevel; at: number }[] = atLadder.map((rung) => ({
    level: deviceRank[rung.level] >= floorRank ? rung.level : (input.floor ?? rung.level),
    at: anchor + rung.afterMs,
  }));
  if (humanChannelAllowed(input)) planned.push({ level: "HUMAN", at: anchor + AT_HUMAN_AFTER_MS });
  const allowed = planned.filter(
    (p) => p.at < task.deadline.getTime() && !quietIntervalAt(new Date(p.at), recipient.quietHours),
  );
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
  if (task.timing === "at" && task.remindAt) return planAt(input, task.remindAt);
  const window = pokeWindow(task.deadline, recipient, now);
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
