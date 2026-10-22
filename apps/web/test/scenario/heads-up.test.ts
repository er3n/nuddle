// "Zaten yaptın mı?" before a loud poke (NUD-129, docs/domain.md, Already done?). The heads-up itself lives on the
// phone; what reaches the server is the normal Done, which must stop every later poke.
import { describe, expect, it } from "vitest";
import { at, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

const rings = (r: { rang: string; at: Date }[]) => r.map((x) => `${x.rang}@${hhmm(x.at)}`);

describe("scenarios: Zaten yaptın mı? (NUD-129)", () => {
  it("Bitti on the heads-up before the sound step stops the later pokes and nobody is told", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);

    // Eren unlocks at 18:22, 8 minutes before the sound step, and taps Bitti on the heads-up.
    await world.advanceTo(at("2026-10-12T18:22:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00"]);
    expect(eren.phone.pendingAlarms().map((a) => a.level)).toContain("SOUND");
    expect(await eren.command(taskId, { type: "done", doneAt: at("2026-10-12T18:22:00") })).toEqual(
      {
        ok: true,
      },
    );

    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(world.task(taskId)).toMatchObject({ status: "DONE", late: false });
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00"]);
    expect(eren.phone.pendingAlarms()).toEqual([]);
    const types = world.eventsOf(taskId).map((e) => e.type);
    expect(types).not.toContain("poke_lost");
    expect(types).not.toContain("human_notified");
    expect(world.push.to("sibel").map((p) => p.event)).not.toContain("human_notified");
    expect(world.pendingActions(taskId)).toEqual([]);
    expect(world.violations).toEqual([]);
  });
});
