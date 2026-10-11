import { describe, expect, it } from "vitest";
import { planPokes, pokeWindowEnd } from "../../src/domain/plan-pokes";
import { isQuiet } from "../../src/domain/quiet-hours";
import { MINUTE } from "../../src/domain/time";
import type { Person, PokeLevel, PokeStep } from "../../src/domain/types";
import { aPerson, aTask } from "../builders";

const selin = aPerson("Selin");
const eren = aPerson("Eren");
const rank: Record<PokeLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2, HUMAN: 3 };

function plan(deadline: string, now: string, opts: { recipient?: Person; self?: boolean } = {}) {
  const recipient = opts.recipient ?? eren;
  return planPokes({
    task: aTask()
      .from(selin)
      .forRecipient(opts.self ? selin : recipient)
      .withDeadline(deadline)
      .build(),
    recipient: opts.self ? selin : recipient,
    sender: selin,
    reachability: "FULL_SCREEN",
    history: [],
    now: new Date(now),
  });
}

const summary = (steps: PokeStep[]) =>
  steps.map((s) => `${s.level}@${s.plannedAt.toISOString().slice(11, 16)}`);

describe("planPokes v0", () => {
  it("places the full ladder in the last hour before the deadline", () => {
    // Deadline 09:00 Istanbul = 06:00Z.
    const steps = plan("2026-10-12T09:00:00+03:00", "2026-10-11T20:00:00+03:00");
    expect(summary(steps)).toEqual([
      "SILENT@05:00",
      "SOUND@05:30",
      "FULL_SCREEN@05:45",
      "HUMAN@05:55",
    ]);
    expect(steps.map((s) => s.seq)).toEqual([1, 2, 3, 4]);
  });

  it("shrinks the ladder proportionally when less than an hour is left", () => {
    const steps = plan("2026-10-11T20:30:00+03:00", "2026-10-11T20:00:00+03:00");
    expect(summary(steps)).toEqual([
      "SILENT@17:00",
      "SOUND@17:15",
      "FULL_SCREEN@17:22",
      "HUMAN@17:27",
    ]);
  });

  it("ends before quiet hours when the deadline falls inside them", () => {
    // 06:00 is inside 23:00-07:00, so the window ends at 23:00 the evening before.
    const steps = plan("2026-10-12T06:00:00+03:00", "2026-10-11T12:00:00+03:00");
    expect(pokeWindowEnd(new Date("2026-10-12T06:00:00+03:00"), eren)).toEqual(
      new Date("2026-10-11T23:00:00+03:00"),
    );
    expect(summary(steps)).toEqual([
      "SILENT@19:00",
      "SOUND@19:30",
      "FULL_SCREEN@19:45",
      "HUMAN@19:55",
    ]);
  });

  it("starts the window when quiet hours end", () => {
    // Deadline 07:30: only 07:00-07:30 is outside quiet hours.
    const steps = plan("2026-10-12T07:30:00+03:00", "2026-10-11T20:00:00+03:00");
    expect(summary(steps)).toEqual([
      "SILENT@04:00",
      "SOUND@04:15",
      "FULL_SCREEN@04:22",
      "HUMAN@04:27",
    ]);
  });

  it("follows the recipient's own time zone", () => {
    const london = aPerson("Eren", {
      quietHours: { start: "23:00", end: "07:00", timeZone: "Europe/London" },
    });
    // 07:30 London (BST, +01:00) = 06:30Z; quiet ends 06:00Z.
    const steps = plan("2026-10-12T07:30:00+01:00", "2026-10-11T20:00:00+03:00", {
      recipient: london,
    });
    expect(steps[0]?.plannedAt).toEqual(new Date("2026-10-12T06:00:00Z"));
  });

  it("plans nothing when no time is left outside quiet hours", () => {
    expect(plan("2026-10-12T06:00:00+03:00", "2026-10-11T23:30:00+03:00")).toEqual([]);
  });

  it("uses a single SOUND step when under two minutes are left", () => {
    const steps = plan("2026-10-11T20:01:00+03:00", "2026-10-11T20:00:00+03:00");
    expect(summary(steps)).toEqual(["SOUND@17:00"]);
  });

  it("has no HUMAN step for a task to self", () => {
    const steps = plan("2026-10-12T09:00:00+03:00", "2026-10-11T20:00:00+03:00", { self: true });
    expect(steps.map((s) => s.level)).toEqual(["SILENT", "SOUND", "FULL_SCREEN"]);
  });

  it("has no HUMAN step when either human channel switch is off", () => {
    const off = aPerson("Eren", { useConnectionsForMyTasks: false });
    const steps = plan("2026-10-12T09:00:00+03:00", "2026-10-11T20:00:00+03:00", {
      recipient: off,
    });
    expect(steps.map((s) => s.level)).not.toContain("HUMAN");

    const senderOff = planPokes({
      task: aTask().withDeadline("2026-10-12T09:00:00+03:00").build(),
      recipient: eren,
      sender: aPerson("Selin", { notifyMeForOthersTasks: false }),
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date("2026-10-11T20:00:00+03:00"),
    });
    expect(senderOff.map((s) => s.level)).not.toContain("HUMAN");
  });

  it("resumes from the floor level and keeps it monotonic", () => {
    const steps = planPokes({
      task: aTask().withDeadline("2026-10-12T09:00:00+03:00").build(),
      recipient: eren,
      sender: selin,
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date("2026-10-11T20:00:00+03:00"),
      floor: "SOUND",
    });
    expect(steps.map((s) => s.level)).toEqual(["SOUND", "FULL_SCREEN", "HUMAN"]);
  });

  it("keeps reachability-limited levels in the plan", () => {
    const steps = planPokes({
      task: aTask().withDeadline("2026-10-12T09:00:00+03:00").build(),
      recipient: eren,
      sender: selin,
      reachability: "SOUND",
      history: [],
      now: new Date("2026-10-11T20:00:00+03:00"),
    });
    expect(steps.map((s) => s.level)).toContain("FULL_SCREEN");
  });

  describe("constraints hold for every deadline and now", () => {
    const start = new Date("2026-10-11T00:00:00+03:00").getTime();
    const cases: { now: Date; deadline: Date }[] = [];
    for (let n = 0; n < 24 * 60; n += 47) {
      for (let d = 1; d < 36 * 60; d += 13) {
        cases.push({
          now: new Date(start + n * MINUTE),
          deadline: new Date(start + n * MINUTE + d * MINUTE),
        });
      }
    }

    it(`checks ${String(cases.length)} combinations`, () => {
      for (const { now, deadline } of cases) {
        const steps = planPokes({
          task: aTask().withDeadline(deadline).build(),
          recipient: eren,
          sender: selin,
          reachability: "FULL_SCREEN",
          history: [],
          now,
        });
        const ctx = `now ${now.toISOString()} deadline ${deadline.toISOString()}`;
        const device = steps.filter((s) => s.level !== "HUMAN");
        // SILENT is never the only step.
        if (device.length === 1) expect(device[0]?.level, ctx).not.toBe("SILENT");
        for (let i = 0; i < steps.length; i++) {
          const s = steps[i];
          if (!s) continue;
          expect(isQuiet(s.plannedAt, eren.quietHours), ctx).toBe(false);
          expect(s.plannedAt.getTime(), ctx).toBeGreaterThanOrEqual(now.getTime());
          expect(s.plannedAt.getTime(), ctx).toBeLessThan(deadline.getTime());
          const prev = steps[i - 1];
          if (prev) {
            // Escalation is monotonic, and time moves forward.
            expect(rank[s.level], ctx).toBeGreaterThanOrEqual(rank[prev.level]);
            expect(s.plannedAt.getTime(), ctx).toBeGreaterThanOrEqual(prev.plannedAt.getTime());
          }
        }
        // HUMAN may only be the last step.
        expect(
          steps.slice(0, -1).some((s) => s.level === "HUMAN"),
          ctx,
        ).toBe(false);
      }
    });
  });
});

describe("planPokes v0 at the quiet hours boundary", () => {
  it("pokes the evening before when the deadline is right at the end of quiet hours", () => {
    const steps = planPokes({
      task: aTask().withDeadline("2026-10-12T07:01:00+03:00").build(),
      recipient: aPerson("Eren"),
      sender: aPerson("Selin"),
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date("2026-10-11T20:00:00+03:00"),
    });
    expect(steps.at(-1)?.plannedAt.getTime()).toBeLessThan(
      new Date("2026-10-11T23:00:00+03:00").getTime(),
    );
    expect(steps).toHaveLength(4);
  });
});
