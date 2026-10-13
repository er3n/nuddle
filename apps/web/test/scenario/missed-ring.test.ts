// Scenario list from docs/testing.md (Scenario tests): pokes that did not ring (docs/domain.md, Lost poke;
// docs/ux/platform.md, Missed-ring detection; 2g).
import { describe, expect, it } from "vitest";
import type { Client, World } from "./harness";
import { at, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

/** The phone is in use at `time` (the app syncs), so the server sees it online after the alarm was due. */
async function seenOnlineAt(world: World, who: Client, time: string): Promise<void> {
  await world.advanceTo(at(time));
  await who.phone.sync();
}

describe("scenarios: missed ring", () => {
  it("alarms killed while the phone is online: each lost poke reaches only that phone, and poking goes on", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    eren.phone.alarmsKilled = true;

    for (const time of ["2026-10-12T08:01:00", "2026-10-12T08:31:00", "2026-10-12T08:46:00"])
      await seenOnlineAt(world, eren, time);
    await world.advanceTo(at("2026-10-12T08:56:00"));
    expect(eren.phone.ringsFor(taskId)).toEqual([]);
    const lost = world.eventsOf(taskId).filter((e) => e.type === "poke_lost");
    expect(lost.map((e) => e.payload)).toEqual([
      { seq: 1, level: "SILENT", plannedAt: at("2026-10-12T08:00:00").toISOString() },
      { seq: 2, level: "SOUND", plannedAt: at("2026-10-12T08:30:00").toISOString() },
      { seq: 3, level: "FULL_SCREEN", plannedAt: at("2026-10-12T08:45:00").toISOString() },
    ]);
    // Eren's phone syncs on the push and has what 2g needs: when the poke should have rung.
    expect(world.push.to("eren").map((p) => p.event)).toContain("poke_lost");
    expect(
      eren.phone.events
        .filter((e) => e.type === "poke_lost")
        .map((e) => hhmm(new Date(String(e.payload.plannedAt)))),
    ).toEqual(["08:00", "08:30", "08:45"]);
    // Sibel is not told about Eren's phone; the human channel still fires as planned.
    expect(world.push.to("sibel").map((p) => p.event)).not.toContain("poke_lost");
    expect(world.push.to("sibel").map((p) => p.event)).toContain("human_notified");
    expect(world.task(taskId).status).toBe("SCHEDULED");
    expect(world.violations).toEqual([]);
  });

  it("an offline phone is not evidence of a lost poke", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    eren.phone.online = false;

    await world.advanceTo(at("2026-10-12T08:56:00"));
    expect(world.eventsOf(taskId).map((e) => e.type)).not.toContain("poke_lost");
    expect(world.violations).toEqual([]);
  });

  it("once the alarms ring again, only the poke that did not ring counts as lost", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    eren.phone.alarmsKilled = true;

    await seenOnlineAt(world, eren, "2026-10-12T08:01:00");
    await world.advanceTo(at("2026-10-12T08:10:00"));
    eren.phone.alarmsKilled = false;
    await world.advanceTo(at("2026-10-12T08:56:00"));

    expect(eren.phone.ringsFor(taskId).map((r) => `${r.rang}@${hhmm(r.at)}`)).toEqual([
      "SOUND@08:30",
      "FULL_SCREEN@08:45",
    ]);
    expect(
      world
        .eventsOf(taskId)
        .filter((e) => e.type === "poke_lost")
        .map((e) => e.payload.seq),
    ).toEqual([1]);
    expect(world.violations).toEqual([]);
  });
});
