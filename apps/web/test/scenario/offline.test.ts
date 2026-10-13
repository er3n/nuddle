// Scenario list from docs/testing.md (Scenario tests): a phone without a connection (6a).
import { describe, expect, it } from "vitest";
import { v7 as uuidv7 } from "uuid";
import { at, sibelAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: offline", () => {
  it("Bitti tapped offline before the deadline counts when it reaches the server after it", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);

    // Bitti at 18:40 with no connection: the alarms stop on the phone, the command waits in the outbox.
    await world.advanceTo(at("2026-10-12T18:40:00"));
    eren.phone.stopLocal(taskId);
    await world.advanceTo(at("2026-10-12T19:30:00"));
    expect(world.task(taskId).status).toBe("MISSED");

    expect(await eren.command(taskId, { type: "done", doneAt: at("2026-10-12T18:40:00") })).toEqual(
      {
        ok: true,
      },
    );
    expect(world.task(taskId)).toMatchObject({ status: "DONE", late: false });
    expect(world.pendingActions(taskId)).toEqual([]);
    expect(world.violations).toEqual([]);
  });

  it("Bitti tapped after the deadline does not undo the miss", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:30:00"));

    for (const doneAt of [at("2026-10-12T19:10:00"), undefined]) {
      expect(await eren.command(taskId, { type: "done", doneAt })).toEqual({
        ok: false,
        error: "wrong_status",
      });
    }
    // A clock that runs ahead does not help either: a time after the server's now is not believed.
    expect(await eren.command(taskId, { type: "done", doneAt: at("2026-10-12T21:00:00") })).toEqual(
      { ok: false, error: "wrong_status" },
    );
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("a text written offline and sent twice with its key makes one task", async () => {
    const { world, sibel } = await sibelAndEren();
    const key = uuidv7();

    const first = await sibel.write(TEXT.trashToEren, key);
    const again = await sibel.write(TEXT.trashToEren, key);
    expect(first.kind).toBe("created");
    expect(again).toEqual(first);
    expect(world.tasksWrittenBy("sibel")).toHaveLength(1);
  });
});
