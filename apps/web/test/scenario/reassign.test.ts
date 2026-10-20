// Kime changed on a task that was already written (NUD-114, docs/domain.md, Task states).
import { describe, expect, it } from "vitest";
import { at, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

const rings = (r: { rang: string; at: Date }[]) => r.map((x) => `${x.rang}@${hhmm(x.at)}`);

describe("scenarios: Kime changed (NUD-114)", () => {
  it("a task to self goes to Eren: it closes for Sibel and starts for Eren with its own plan", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const oldId = await writeTask(sibel, TEXT.selfSport);
    const before = world.task(oldId);

    const r = await sibel.reassign(oldId, eren);
    if (!r.ok) throw new Error(r.error);

    expect(world.task(oldId).status).toBe("CANCELLED");
    expect(world.eventsOf(oldId).at(-1)).toMatchObject({
      type: "cancelled",
      payload: { reason: "reassigned", to: eren.uuid },
    });
    expect(world.task(r.taskId)).toMatchObject({
      senderId: sibel.id,
      recipientId: eren.id,
      title: before.title,
      deadline: before.deadline,
      finishMode: before.finishMode,
      rawText: before.rawText,
      status: "SCHEDULED",
    });
    expect(eren.phone.received).toContainEqual(
      expect.objectContaining({ hint: "plan_changed", taskId: r.taskId }),
    );
    // Nothing tells Sibel her own task was cancelled.
    expect(sibel.phone.received.map((p) => p.hint)).not.toContain("task_cancelled");
    expect(sibel.phone.pendingAlarms().filter((a) => a.taskId === oldId)).toEqual([]);

    await world.advanceTo(at("2026-10-12T08:01:00"));
    expect(sibel.phone.ringsFor(oldId)).toEqual([]);
    expect(eren.phone.ringsFor(r.taskId).length).toBeGreaterThan(0);
    expect(world.violations).toEqual([]);
  });

  it("a task to Eren goes to Defne: Eren's alarms stop and his phone learns why", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const defne = await world.addPerson("Defne");
    await world.connect(sibel, defne, "child", "parent");
    const oldId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:01:00"));
    expect(rings(eren.phone.ringsFor(oldId))).toEqual(["SILENT@18:00"]);

    const r = await sibel.reassign(oldId, defne);
    if (!r.ok) throw new Error(r.error);

    expect(eren.phone.received).toContainEqual(
      expect.objectContaining({ hint: "task_cancelled", taskId: oldId }),
    );
    expect(eren.phone.events.at(-1)).toMatchObject({
      taskId: oldId,
      type: "cancelled",
      payload: { reason: "reassigned" },
    });
    expect(eren.phone.pendingAlarms()).toEqual([]);
    expect(world.task(r.taskId)).toMatchObject({ recipientId: defne.id, status: "SCHEDULED" });

    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(rings(eren.phone.ringsFor(oldId))).toEqual(["SILENT@18:00"]);
    expect(defne.phone.ringsFor(r.taskId).length).toBeGreaterThan(0);
    expect(world.violations).toEqual([]);
  });

  it("only the sender, only to a connection, only to someone else", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const stranger = await world.addPerson("Defne");
    const taskId = await writeTask(sibel, TEXT.trashToEren);

    expect(await eren.reassign(taskId, sibel)).toEqual({ ok: false, error: "not_sender" });
    expect(await sibel.reassign(taskId, stranger)).toEqual({
      ok: false,
      error: "not_connected",
    });
    expect(await sibel.reassign(taskId, eren)).toEqual({ ok: false, error: "wrong_status" });
    // A failed reassign leaves the task as it was.
    expect(world.task(taskId)).toMatchObject({ status: "SCHEDULED", recipientId: eren.id });
  });
});
