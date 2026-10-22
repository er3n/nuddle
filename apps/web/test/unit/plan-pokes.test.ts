import { describe, expect, it } from "vitest";
import { planPokes, pokeWindowEnd } from "../../src/domain/plan-pokes";
import { isQuiet } from "../../src/domain/quiet-hours";
import { MINUTE } from "../../src/domain/time";
import type { Person, PokeLevel, PokeStep } from "../../src/domain/types";
import { aPerson, aTask } from "../builders";

const sibel = aPerson("Sibel");
const eren = aPerson("Eren");
const rank: Record<PokeLevel, number> = { SILENT: 0, SOUND: 1, FULL_SCREEN: 2, HUMAN: 3 };

function plan(deadline: string, now: string, opts: { recipient?: Person; self?: boolean } = {}) {
  const recipient = opts.recipient ?? eren;
  return planPokes({
    task: aTask()
      .from(sibel)
      .forRecipient(opts.self ? sibel : recipient)
      .withDeadline(deadline)
      .build(),
    recipient: opts.self ? sibel : recipient,
    sender: sibel,
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
      sender: aPerson("Sibel", { notifyMeForOthersTasks: false }),
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date("2026-10-11T20:00:00+03:00"),
    });
    expect(senderOff.map((s) => s.level)).not.toContain("HUMAN");
  });

  it("has no HUMAN step when the sender turned the switch off for this connection (5b)", () => {
    const input = {
      task: aTask().withDeadline("2026-10-12T09:00:00+03:00").build(),
      recipient: eren,
      sender: sibel,
      reachability: "FULL_SCREEN" as const,
      history: [],
      now: new Date("2026-10-11T20:00:00+03:00"),
    };
    expect(planPokes({ ...input, senderEdge: { notifyMe: false } }).map((s) => s.level)).toEqual([
      "SILENT",
      "SOUND",
      "FULL_SCREEN",
    ]);
    expect(planPokes({ ...input, senderEdge: { notifyMe: true } }).map((s) => s.level)).toContain(
      "HUMAN",
    );
  });

  it("resumes from the floor level and keeps it monotonic", () => {
    const steps = planPokes({
      task: aTask().withDeadline("2026-10-12T09:00:00+03:00").build(),
      recipient: eren,
      sender: sibel,
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
      sender: sibel,
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
          sender: sibel,
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
      sender: aPerson("Sibel"),
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

describe("planPokes v0, an at task (ADR 0017)", () => {
  function planAt(
    moment: string,
    now: string,
    opts: { missAfterMinutes?: number; self?: boolean; floor?: "SOUND" | "FULL_SCREEN" } = {},
  ) {
    const recipient = opts.self ? sibel : eren;
    return planPokes({
      task: aTask()
        .from(sibel)
        .forRecipient(recipient)
        .at(moment, opts.missAfterMinutes ?? 60)
        .build(),
      recipient,
      sender: sibel,
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date(now),
      ...(opts.floor ? { floor: opts.floor } : {}),
    });
  }

  it("starts at the moment and never pokes before it", () => {
    // "2 dakika sonra su iç" written at 18:40 Istanbul (15:40Z).
    const steps = planAt("2026-10-13T18:42:00+03:00", "2026-10-13T18:40:00+03:00", { self: true });
    expect(summary(steps)).toEqual(["SOUND@15:42", "FULL_SCREEN@15:47", "FULL_SCREEN@16:02"]);
    expect(steps.every((s) => s.plannedAt >= new Date("2026-10-13T15:42:00Z"))).toBe(true);
  });

  it("adds the human step 30 minutes after the moment for someone else's task", () => {
    const steps = planAt("2026-10-13T18:42:00+03:00", "2026-10-13T18:40:00+03:00");
    expect(summary(steps)).toEqual([
      "SOUND@15:42",
      "FULL_SCREEN@15:47",
      "FULL_SCREEN@16:02",
      "HUMAN@16:12",
    ]);
  });

  it("drops steps at or after the deadline", () => {
    const steps = planAt("2026-10-13T18:42:00+03:00", "2026-10-13T18:40:00+03:00", {
      self: true,
      missAfterMinutes: 15,
    });
    expect(summary(steps)).toEqual(["SOUND@15:42", "FULL_SCREEN@15:47"]);
  });

  it("rings inside quiet hours: the sender asked for that time (NUD-70, open-questions#47)", () => {
    // 22:50 moment, 23:00 starts quiet hours: the ladder goes on through them.
    const steps = planAt("2026-10-13T22:50:00+03:00", "2026-10-13T20:00:00+03:00", { self: true });
    expect(summary(steps)).toEqual(["SOUND@19:50", "FULL_SCREEN@19:55", "FULL_SCREEN@20:10"]);
    // A 06:30 wake-up rings at 06:30.
    const wake = planAt("2026-10-14T06:30:00+03:00", "2026-10-13T22:00:00+03:00", {
      self: true,
      missAfterMinutes: 15,
    });
    expect(summary(wake)).toEqual(["SOUND@03:30", "FULL_SCREEN@03:35"]);
  });

  it("starts the ladder at the moment minus the lead (NUD-70)", () => {
    // Pick the kids up at 17:30, a 20 minute walk: poking starts at 17:10.
    const task = aTask().from(sibel).forRecipient(eren).at("2026-10-13T17:30:00+03:00", 15).build();
    const steps = planPokes({
      task: { ...task, leadMinutes: 20 },
      recipient: eren,
      sender: sibel,
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date("2026-10-13T14:00:00+03:00"),
    });
    expect(summary(steps)).toEqual([
      "SOUND@14:10",
      "FULL_SCREEN@14:15",
      "FULL_SCREEN@14:30",
      "HUMAN@14:40",
    ]);
  });

  it("tells the sender 5 minutes before the deadline when 30 minutes after the start is too late", () => {
    // A wake-up with a 15 minute grace: HUMAN at the moment plus 10, after the last device step.
    const steps = planAt("2026-10-13T07:00:00+03:00", "2026-10-12T22:00:00+03:00", {
      missAfterMinutes: 15,
    });
    expect(summary(steps)).toEqual(["SOUND@04:00", "FULL_SCREEN@04:05", "HUMAN@04:10"]);
  });

  it("starts from now when poking resumes after the moment, at least at the level reached", () => {
    const steps = planAt("2026-10-13T18:42:00+03:00", "2026-10-13T18:57:00+03:00", {
      self: true,
      floor: "FULL_SCREEN",
    });
    expect(summary(steps)).toEqual(["FULL_SCREEN@15:57", "FULL_SCREEN@16:02", "FULL_SCREEN@16:17"]);
  });

  it("plans nothing when the deadline has passed", () => {
    expect(
      planAt("2026-10-13T18:00:00+03:00", "2026-10-13T19:30:00+03:00", {
        self: true,
        missAfterMinutes: 60,
      }),
    ).toEqual([]);
  });
});

describe("planPokes v0, a one-time message (NUD-79)", () => {
  function planNotice(moment: string, now: string) {
    return planPokes({
      task: aTask().from(sibel).forRecipient(eren).at(moment).withFinishMode("notice").build(),
      recipient: eren,
      sender: sibel,
      reachability: "FULL_SCREEN",
      history: [],
      now: new Date(now),
    });
  }

  it("rings a time given a minute away at that time, not at once (NUD-140)", () => {
    // "bir dakika sonra Sibel'e yanakau de" written at 21:19:39, the time 21:20:38.
    const steps = planNotice("2026-10-22T21:20:38+03:00", "2026-10-22T21:19:39+03:00");
    expect(steps).toEqual([
      { seq: 1, plannedAt: new Date("2026-10-22T18:20:38Z"), level: "SOUND", firedAt: null },
    ]);
  });

  it("rings a message without a time now: its time is the moment it was written", () => {
    const steps = planNotice("2026-10-22T21:19:39+03:00", "2026-10-22T21:19:39+03:00");
    expect(summary(steps)).toEqual(["SOUND@18:19"]);
    expect(steps[0]?.plannedAt).toEqual(new Date("2026-10-22T18:19:39Z"));
  });

  it("rings at once when its time passed before planning, and never after the deadline", () => {
    expect(summary(planNotice("2026-10-22T21:19:00+03:00", "2026-10-22T21:19:39+03:00"))).toEqual([
      "SOUND@18:19",
    ]);
    expect(planNotice("2026-10-22T20:00:00+03:00", "2026-10-22T21:30:00+03:00")).toEqual([]);
  });
});
