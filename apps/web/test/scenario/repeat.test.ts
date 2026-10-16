// Repeating tasks (ADR 0021): one open occurrence at a time; done, missed or dropped brings the
// next one at the rule's next time; a sender's cancel ends the series.
import { describe, expect, it } from "vitest";
import type { Task } from "../../src/domain/types";
import { at, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

/** The one open occurrence of the series, which a scenario expects to exist. */
function openOne(tasks: Task[]): Task {
  const open = tasks.filter((t) => t.status === "SCHEDULED");
  const first = open[0];
  if (open.length !== 1 || first === undefined) {
    throw new Error(`expected one open occurrence, found ${String(open.length)}`);
  }
  return first;
}

describe("scenarios: repeating tasks", () => {
  it("done brings tomorrow's occurrence with the same rule and series", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const first = await writeTask(sibel, TEXT.dailyMedicineToEren);
    const task = world.task(first);
    expect(task.repeat).toEqual({ every: "day", time: "09:00" });
    expect(task.seriesId).toBe(first);
    expect(hhmm(task.remindAt ?? task.deadline)).toBe("09:00");

    await world.advanceTo(at("2026-10-12T09:05:00"));
    expect(await eren.command(first, { type: "done" })).toEqual({ ok: true });
    const next = openOne(world.tasksWrittenBy("sibel"));
    expect(next.id).not.toBe(first);
    expect(next.seriesId).toBe(first);
    expect(next.repeat).toEqual({ every: "day", time: "09:00" });
    expect(next.title).toBe("İlacını al");
    expect(next.timing).toBe("at");
    expect(next.remindAt).toEqual(at("2026-10-13T09:00:00"));
    // The same grace as the first occurrence.
    expect(next.deadline).toEqual(at("2026-10-13T11:00:00"));
  });

  it("an occurrence done early still goes to the next day, a missed one to the day after it expires", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const first = await writeTask(sibel, TEXT.dailyMedicineToEren);
    // Done on Sunday night for Monday's 09:00: the next is Tuesday, not Monday again.
    expect(await eren.command(first, { type: "done" })).toEqual({ ok: true });
    const second = openOne(world.tasksWrittenBy("sibel"));
    expect(second.remindAt).toEqual(at("2026-10-13T09:00:00"));

    // Tuesday's passes untouched: missed at 11:00, and Wednesday's is created right then.
    await world.advanceTo(at("2026-10-13T11:00:30"));
    expect(world.task(second.id).status).toBe("MISSED");
    const third = openOne(world.tasksWrittenBy("sibel"));
    expect(third.remindAt).toEqual(at("2026-10-14T09:00:00"));
    expect(third.seriesId).toBe(first);
  });

  it("Bugün olmaz skips to the next occurrence; a cancel by the sender ends the series", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const first = await writeTask(sibel, TEXT.weeklyTrashToEren);
    expect(world.task(first).deadline).toEqual(at("2026-10-13T19:00:00"));
    expect(await eren.command(first, { type: "cant_today", note: null })).toEqual({ ok: true });
    const second = openOne(world.tasksWrittenBy("sibel"));
    expect(second.deadline).toEqual(at("2026-10-20T19:00:00"));

    expect(await sibel.command(second.id, { type: "cancel", note: null })).toEqual({ ok: true });
    expect(world.tasksWrittenBy("sibel").filter((t) => t.status === "SCHEDULED")).toHaveLength(0);
  });

  it("a one-off task never gets a successor", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect(world.task(taskId).repeat).toBeNull();
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });
    expect(world.tasksWrittenBy("sibel")).toHaveLength(1);
  });

  it("the first occurrence comes from the rule, even when the model's time has passed (NUD-83)", async () => {
    const { world, sibel } = await sibelAndEren();
    // Sunday 20:00; the model said today 07:00. No question: Monday 07:00 from the weekdays rule.
    const taskId = await writeTask(sibel, TEXT.weekdaysWakeSelf);
    const task = world.task(taskId);
    expect(task.repeat).toEqual({ every: "weekdays", time: "07:00" });
    expect(task.remindAt).toEqual(at("2026-10-12T07:00:00"));
    expect(task.seriesId).toBe(taskId);
  });
});
