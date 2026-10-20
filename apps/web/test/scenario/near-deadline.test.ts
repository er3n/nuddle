// A `by` time only minutes away is a moment (NUD-116, docs/domain.md): on 2026-10-17 a deadline picked
// 15 seconds ahead rang once and was missed right after.
import { v7 as uuidv7 } from "uuid";
import { describe, expect, it } from "vitest";
import { addMs, MINUTE } from "../../src/domain/time";
import { hhmm, sibelAndEren } from "./fixtures";

const rings = (r: { rang: string; at: Date }[]) => r.map((x) => `${x.rang}@${hhmm(x.at)}`);

describe("scenarios: a deadline only minutes away (NUD-116)", () => {
  it("rings the at ladder from that time and is missed only after the grace", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const start = world.clock.now();
    const picked = addMs(start, 1 * MINUTE);

    const created = await sibel.api.POST("/api/v1/commands/create_task", {
      body: {
        idempotencyKey: uuidv7(),
        recipientId: eren.uuid,
        title: "Dışarıdan bir şey al",
        detail: null,
        deadline: picked.toISOString(),
        finishMode: "done",
        rawText: "disaridan birsey istiyor musun",
      },
    });
    const taskId = created.data?.taskId;
    if (!taskId) throw new Error(JSON.stringify(created.error));
    await world.refresh();
    expect(world.task(taskId)).toMatchObject({ timing: "at", remindAt: picked });

    await world.advanceTo(addMs(start, 30 * MINUTE));
    expect(world.task(taskId).status).toBe("SCHEDULED");
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      `SOUND@${hhmm(picked)}`,
      `FULL_SCREEN@${hhmm(addMs(picked, 5 * MINUTE))}`,
      `FULL_SCREEN@${hhmm(addMs(picked, 20 * MINUTE))}`,
    ]);

    await world.advanceTo(addMs(start, 62 * MINUTE));
    expect(world.task(taskId).status).toBe("MISSED");
    expect(world.violations).toEqual([]);
  });
});
