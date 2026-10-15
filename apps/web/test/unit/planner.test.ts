import { describe, expect, it } from "vitest";
import {
  checkPlan,
  historyItem,
  type CheckInput,
  type PlannerInput,
} from "../../src/domain/planner";
import type { PokeLevel, TaskEvent } from "../../src/domain/types";
import { buildPlannerPrompt, OpenRouterPlanner } from "../../src/infra/llm/openrouter-planner";
import { aPerson, aTask } from "../builders";

const sibel = aPerson("Sibel");
const eren = aPerson("Eren");
// Deadline 09:00 Istanbul (06:00Z); written the evening before.
const task = aTask()
  .from(sibel)
  .forRecipient(eren)
  .withDeadline("2026-10-12T09:00:00+03:00")
  .build();
const base: CheckInput = {
  task,
  recipient: eren,
  sender: sibel,
  senderEdge: { notifyMe: true },
  notBefore: new Date("2026-10-11T20:00:00+03:00"),
  floor: "SILENT",
};

const step = (hhmm: string, level: PokeLevel, day = "2026-10-12") => ({
  at: new Date(`${day}T${hhmm}:00+03:00`),
  level,
});
const check = (steps: { at: Date; level: PokeLevel }[], input: Partial<CheckInput> = {}) =>
  checkPlan({ steps, reason: "test" }, { ...base, ...input });
const summary = (steps: ReturnType<typeof checkPlan>) =>
  steps?.map((s) => `${String(s.seq)}:${s.level}@${s.plannedAt.toISOString().slice(11, 16)}`) ??
  null;

describe("checkPlan (ADR 0017)", () => {
  it("keeps a valid plan in time order and numbers it", () => {
    expect(
      summary(
        check([
          step("08:30", "FULL_SCREEN"),
          step("07:30", "SOUND"),
          step("08:50", "HUMAN"),
          step("08:00", "FULL_SCREEN"),
        ]),
      ),
    ).toEqual(["1:SOUND@04:30", "2:FULL_SCREEN@05:00", "3:FULL_SCREEN@05:30", "4:HUMAN@05:50"]);
  });

  it("drops steps before notBefore, at or after the deadline, and inside quiet hours", () => {
    expect(
      summary(
        check([
          step("19:00", "SOUND", "2026-10-11"),
          step("02:00", "SOUND"),
          step("08:00", "SOUND"),
          step("08:30", "FULL_SCREEN"),
          step("09:00", "FULL_SCREEN"),
        ]),
      ),
    ).toEqual(["1:SOUND@05:00", "2:FULL_SCREEN@05:30"]);
  });

  it("lets an at task start before its moment by its lead, and ring inside quiet hours (NUD-70)", () => {
    // Pick up at 08:00 with a 45 minute lead: 07:15 is allowed although quiet hours end at 07:00 anyway;
    // 06:50 is before the start and dropped. A 06:30 wake-up keeps its 06:30 step.
    const pickup = {
      ...aTask().from(sibel).forRecipient(eren).at("2026-10-12T08:00:00+03:00", 15).build(),
      leadMinutes: 45,
    };
    expect(
      summary(
        check([step("06:50", "SOUND"), step("07:15", "SOUND"), step("07:40", "FULL_SCREEN")], {
          task: pickup,
        }),
      ),
    ).toEqual(["1:SOUND@04:15", "2:FULL_SCREEN@04:40"]);
    const wake = aTask().from(sibel).forRecipient(eren).at("2026-10-12T06:30:00+03:00", 15).build();
    expect(
      summary(check([step("06:30", "SOUND"), step("06:35", "FULL_SCREEN")], { task: wake })),
    ).toEqual(["1:SOUND@03:30", "2:FULL_SCREEN@03:35"]);
  });

  it("never pokes before the moment of an at task", () => {
    const at = aTask().from(sibel).forRecipient(eren).at("2026-10-12T08:00:00+03:00", 60).build();
    expect(
      summary(
        check([step("07:50", "SOUND"), step("08:00", "SOUND"), step("08:05", "FULL_SCREEN")], {
          task: at,
        }),
      ),
    ).toEqual(["1:SOUND@05:00", "2:FULL_SCREEN@05:05"]);
  });

  it("never drops a level, and nothing goes below the floor", () => {
    expect(
      summary(
        check([step("07:00", "FULL_SCREEN"), step("07:30", "SOUND"), step("08:00", "FULL_SCREEN")]),
      ),
    ).toEqual(["1:FULL_SCREEN@04:00", "2:FULL_SCREEN@05:00"]);
    expect(
      summary(
        check([step("07:00", "SOUND"), step("08:00", "FULL_SCREEN")], { floor: "FULL_SCREEN" }),
      ),
    ).toEqual(["1:FULL_SCREEN@05:00"]);
  });

  it("keeps device steps 3 minutes apart and at most 8 of them", () => {
    expect(
      summary(
        check([step("08:00", "SOUND"), step("08:02", "SOUND"), step("08:03", "FULL_SCREEN")]),
      ),
    ).toEqual(["1:SOUND@05:00", "2:FULL_SCREEN@05:03"]);
    const many = Array.from({ length: 12 }, (_, i) =>
      step(`08:${String(i * 4).padStart(2, "0")}`, "SOUND"),
    );
    expect(check(many)).toHaveLength(8);
  });

  it("allows one HUMAN step, after the last device step and within the last hour, only when allowed", () => {
    // The deadline is 09:00: a HUMAN at 08:30 stays, the device steps after it are kept too, and a
    // second HUMAN is ignored. The 08:40 FULL_SCREEN pushes the HUMAN to 08:43.
    expect(
      summary(
        check([
          step("08:00", "SOUND"),
          step("08:30", "HUMAN"),
          step("08:40", "FULL_SCREEN"),
          step("08:50", "HUMAN"),
        ]),
      ),
    ).toEqual(["1:SOUND@05:00", "2:FULL_SCREEN@05:40", "3:HUMAN@05:43"]);
    // An early HUMAN is moved into the last hour (NUD-70), not dropped.
    expect(summary(check([step("07:30", "SOUND"), step("07:40", "HUMAN")]))).toEqual([
      "1:SOUND@04:30",
      "2:HUMAN@05:00",
    ]);
    expect(
      summary(
        check([step("08:00", "SOUND"), step("08:30", "HUMAN")], {
          senderEdge: { notifyMe: false },
        }),
      ),
    ).toEqual(["1:SOUND@05:00"]);
  });

  it("rejects a plan with no device step or only SILENT ones", () => {
    expect(check([])).toBeNull();
    expect(check([step("08:30", "HUMAN")])).toBeNull();
    expect(check([step("08:00", "SILENT"), step("08:30", "SILENT")])).toBeNull();
    expect(check([step("10:00", "SOUND")])).toBeNull();
  });
});

describe("historyItem", () => {
  it("says who sent it, how it ended, which pokes rang and when the recipient acted", () => {
    const done = { ...task, status: "DONE" as const };
    const ev = (type: TaskEvent["type"], at: string, actorId: string | null): TaskEvent => ({
      taskId: task.id,
      actorId,
      type,
      payload: {},
      at: new Date(at),
    });
    const fired = [
      {
        level: "SOUND" as const,
        plannedAt: new Date("2026-10-12T08:30:00+03:00"),
        firedAt: new Date("2026-10-12T08:30:05+03:00"),
      },
      {
        level: "SILENT" as const,
        plannedAt: new Date("2026-10-12T08:00:00+03:00"),
        firedAt: new Date("2026-10-12T08:00:02+03:00"),
      },
    ];
    const item = historyItem(
      done,
      [
        ev("created", "2026-10-11T20:00:00+03:00", "sibel"),
        ev("snoozed", "2026-10-12T08:00:30+03:00", "eren"),
        ev("done", "2026-10-12T08:34:00+03:00", "eren"),
      ],
      fired,
      { name: "Sibel", relationship: "spouse" },
    );
    expect(item).toMatchObject({
      from: "Sibel",
      relationship: "spouse",
      outcome: "DONE",
      actedAt: new Date("2026-10-12T08:34:00+03:00"),
      snoozes: 1,
      cantToday: false,
    });
    expect(item.pokes.map((p) => p.level)).toEqual(["SILENT", "SOUND"]);
    // Acted 3m55s after the second poke that rang.
    expect(item.actedAfter).toEqual({ poke: 2, level: "SOUND", secondsAfter: 235 });
  });
});

describe("OpenRouterPlanner", () => {
  const input: PlannerInput = {
    now: new Date("2026-10-11T20:00:00+03:00"),
    timeZone: "Europe/Istanbul",
    task: {
      rawText: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
      title: "Temizlikçi için para çek",
      detail: "1.000 TL",
      finishMode: "photo",
      timing: "by",
      time: new Date("2026-10-12T09:00:00+03:00"),
      startAt: new Date("2026-10-12T09:00:00+03:00"),
      leadMinutes: 0,
      deadline: new Date("2026-10-12T09:00:00+03:00"),
      notBefore: new Date("2026-10-11T20:00:00+03:00"),
      floor: "SILENT",
      snoozesSoFar: 0,
    },
    sender: { name: "Sibel", self: false },
    recipient: { name: "Eren" },
    relationship: { recipientIsToSender: "spouse", senderIsToRecipient: "spouse" },
    recipientConnections: [{ name: "Sibel", relationship: "spouse" }],
    quietHours: eren.quietHours,
    reachability: "FULL_SCREEN",
    humanAllowed: true,
    profile: null,
    history: [],
  };

  it("sends the task in local time with a strict schema and parses the steps", async () => {
    const bodies: string[] = [];
    const answer = {
      steps: [{ at: "2026-10-12T08:00:00+03:00", level: "SOUND" }],
      reason: "First plan for Eren.",
    };
    const fakeFetch: typeof fetch = (_url, init) => {
      bodies.push(init?.body as string);
      return Promise.resolve(
        new Response(
          JSON.stringify({ choices: [{ message: { content: JSON.stringify(answer) } }] }),
          {
            status: 200,
          },
        ),
      );
    };
    const planner = new OpenRouterPlanner({ apiKey: "k", model: "m/x", fetch: fakeFetch });
    expect(await planner.plan(input)).toEqual({
      steps: [{ at: new Date("2026-10-12T05:00:00Z"), level: "SOUND" }],
      reason: "First plan for Eren.",
    });
    const body = JSON.parse(bodies[0] ?? "{}") as {
      model: string;
      response_format: { json_schema: { name: string; strict: boolean } };
    };
    expect(body.model).toBe("m/x");
    expect(body.response_format.json_schema).toMatchObject({ name: "poke_plan", strict: true });
    const prompt = buildPlannerPrompt(input);
    expect(prompt).toContain('"deadline": "2026-10-12T09:00:00+03:00"');
    expect(prompt).toContain('"weekday": "Sunday"');
  });
});
