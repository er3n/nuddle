// Scenario list from docs/testing.md (Scenario tests): task lifecycles across sender and recipient.
import { describe, expect, it } from "vitest";
import { MISSED_CARD_TTL_MS } from "../../src/domain/task-machine";
import { visibleToRecipient } from "../../src/domain/tick";
import { MINUTE } from "../../src/domain/time";
import { at, emptyWorld, hhmm, selinAndEren, TEXT, writeTask } from "./fixtures";

const rings = (r: { rang: string; at: Date }[]) => r.map((x) => `${x.rang}@${hhmm(x.at)}`);

describe("scenarios: task lifecycle", () => {
  it("photo task: unanswered pokes reach Selin, photo stops poking, auto-approve after 3 hours", async () => {
    // The example in docs/testing.md.
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.photoToEren);

    expect(world.task(taskId)).toMatchObject({ recipientId: "eren", finishMode: "photo" });
    expect(eren.phone.received).toContainEqual(
      expect.objectContaining({ hint: "plan_changed", taskId }),
    );

    await world.advanceTo(at("2026-10-12T08:56:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@08:00",
      "SOUND@08:30",
      "FULL_SCREEN@08:45",
    ]);
    expect(world.push.to("selin").map((p) => p.event)).toContain("human_notified");

    await world.advanceTo(at("2026-10-12T08:58:00"));
    expect(await eren.sendPhoto(taskId)).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");
    expect(eren.phone.pendingAlarms()).toEqual([]);
    expect(world.push.to("selin").map((p) => p.event)).toContain("photo_sent");

    // The deadline applies to sending the photo, not to approval.
    await world.advanceTo(at("2026-10-12T09:30:00"));
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");

    await world.advanceTo(at("2026-10-12T11:58:00"));
    expect(world.task(taskId)).toMatchObject({ status: "DONE" });
    expect(world.eventsOf(taskId).find((e) => e.type === "approved")?.payload).toEqual({
      auto: true,
    });
    expect(world.push.to("eren").map((p) => p.event)).toContain("approved");
    expect(world.photos.deleted).toContain(taskId);
    expect(world.violations).toEqual([]);
  });

  it("snooze: 15 minutes, then 10 as the last one, then no more (open-questions#1)", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.photoToEren);

    await world.advanceTo(at("2026-10-12T08:01:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@08:00"]);
    expect(await eren.command(taskId, { type: "snooze" })).toEqual({ ok: true });

    // Nothing rings while snoozed; the rest of the plan starts when the snooze ends.
    await world.advanceTo(at("2026-10-12T08:15:00"));
    expect(eren.phone.ringsFor(taskId)).toHaveLength(1);
    await world.advanceTo(at("2026-10-12T08:17:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@08:00", "SILENT@08:16"]);

    expect(await eren.command(taskId, { type: "snooze" })).toEqual({ ok: true });
    expect(
      world
        .eventsOf(taskId)
        .filter((e) => e.type === "snoozed")
        .map((e) => e.payload.minutes),
    ).toEqual([15, 10]);

    await world.advanceTo(at("2026-10-12T08:28:00"));
    expect(await eren.command(taskId, { type: "snooze" })).toEqual({
      ok: false,
      error: "snooze_not_allowed",
    });
    expect(world.task(taskId).status).toBe("SCHEDULED");
    expect(world.violations).toEqual([]);
  });

  it("task to self: rings, done, nobody else is told", async () => {
    const world = emptyWorld();
    const eren = await world.addPerson("Eren");
    const taskId = await writeTask(eren, TEXT.selfSport);
    expect(world.task(taskId)).toMatchObject({ senderId: "eren", recipientId: "eren" });

    await world.advanceTo(at("2026-10-12T07:05:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@07:00"]);
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });

    await world.advanceTo(at("2026-10-12T09:00:00"));
    expect(world.task(taskId).status).toBe("DONE");
    expect(eren.phone.ringsFor(taskId)).toHaveLength(1);
    expect(world.eventsOf(taskId).map((e) => e.type)).not.toContain("human_notified");
    expect(world.push.sent.every((p) => p.to === "eren" && p.hint === "plan_changed")).toBe(true);
  });

  it("task to another person, done: poking stops, the sender gets no push", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);

    await world.advanceTo(at("2026-10-12T18:31:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00", "SOUND@18:30"]);
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });

    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(world.task(taskId).status).toBe("DONE");
    expect(eren.phone.ringsFor(taskId)).toHaveLength(2);
    expect(world.push.to("selin").filter((p) => p.taskId === taskId)).toEqual([]);
    expect(world.eventsOf(taskId).map((e) => e.type)).toContain("done");
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("photo + approve", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(taskId);

    await world.advanceBy(10 * MINUTE);
    expect(await selin.command(taskId, { type: "approve" })).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("DONE");
    expect(world.push.to("eren").at(-1)).toMatchObject({ taskId, event: "approved" });
    expect(world.photos.deleted).toContain(taskId);
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("photo + re-request: poking resumes where it stopped, then a second photo is approved", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(taskId);

    await world.advanceTo(at("2026-10-12T08:35:00"));
    expect(await selin.command(taskId, { type: "re_request", note: "Fiş görünmüyor" })).toEqual({
      ok: true,
    });
    expect(world.task(taskId).status).toBe("REJECTED");
    expect(world.push.to("eren").at(-1)).toMatchObject({ hint: "plan_changed", taskId });
    expect(world.eventsOf(taskId).find((e) => e.type === "re_requested")?.payload).toEqual({
      note: "Fiş görünmüyor",
    });

    const before = eren.phone.ringsFor(taskId).length;
    await world.advanceTo(at("2026-10-12T08:50:00"));
    const resumed = eren.phone.ringsFor(taskId).slice(before);
    // SOUND had already rung, so the new plan starts at SOUND, not SILENT.
    expect(resumed.map((r) => r.rang)).toEqual(["SOUND"]);

    await eren.sendPhoto(taskId);
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");
    await selin.command(taskId, { type: "approve" });
    expect(world.task(taskId).status).toBe("DONE");
    expect(world.violations).toEqual([]);
  });

  it("can't today with a note: the sender is told and can give a new time", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:31:00"));

    expect(
      await eren.command(taskId, { type: "cant_today", note: "Bugün eve geç geliyorum" }),
    ).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("DROPPED");
    expect(world.push.to("selin").at(-1)).toMatchObject({ taskId, event: "dropped" });
    expect(world.eventsOf(taskId).find((e) => e.type === "dropped")?.payload).toEqual({
      note: "Bugün eve geç geliyorum",
    });

    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(eren.phone.ringsFor(taskId)).toHaveLength(2);
    expect(world.task(taskId).status).toBe("DROPPED");

    // "Yeni zaman ver"
    expect(
      await selin.command(taskId, { type: "new_deadline", deadline: at("2026-10-13T19:00:00") }),
    ).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("SCHEDULED");
    await world.advanceTo(at("2026-10-13T18:01:00"));
    expect(rings(eren.phone.ringsFor(taskId)).at(-1)).toBe("SILENT@18:00");
  });

  it("missed, then 'Sonradan yaptım'", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);

    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(world.task(taskId).status).toBe("MISSED");
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@18:00",
      "SOUND@18:30",
      "FULL_SCREEN@18:45",
    ]);
    expect(world.push.to("selin").map((p) => p.event)).toEqual(
      expect.arrayContaining(["human_notified", "missed"]),
    );
    expect(world.push.to("eren").at(-1)).toMatchObject({ taskId, event: "missed" });

    expect(await eren.command(taskId, { type: "did_it_later" })).toEqual({ ok: true });
    expect(world.task(taskId)).toMatchObject({ status: "DONE", late: true });
  });

  it("a missed card leaves the recipient's list after one day", async () => {
    const { world, selin } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:00:00"));
    expect(world.task(taskId).status).toBe("MISSED");

    await world.advanceTo(at("2026-10-13T18:59:00"));
    expect(visibleToRecipient(world.task(taskId), MISSED_CARD_TTL_MS, world.clock.now())).toBe(
      true,
    );
    await world.advanceTo(at("2026-10-13T19:00:00"));
    expect(visibleToRecipient(world.task(taskId), MISSED_CARD_TTL_MS, world.clock.now())).toBe(
      false,
    );
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("sender cancel: pokes stop, the recipient is told", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:10:00"));

    expect(await selin.command(taskId, { type: "cancel", note: "Ben hallettim" })).toEqual({
      ok: true,
    });
    expect(world.task(taskId).status).toBe("CANCELLED");
    expect(world.push.to("eren").at(-1)).toMatchObject({ hint: "task_cancelled", taskId });
    expect(eren.phone.pendingAlarms()).toEqual([]);

    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00"]);
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("sender cancel with a delayed push: one late ring, then the fired report stops it", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:10:00"));

    eren.phone.pushesDelayed = true;
    await selin.command(taskId, { type: "cancel", note: null });
    expect(eren.phone.pendingAlarms()).not.toEqual([]);
    eren.phone.pushesDelayed = false;

    await world.advanceTo(at("2026-10-12T20:00:00"));
    // Accepted MVP edge case (ADR 0005): SOUND rings once, its report is answered with a cancel.
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00", "SOUND@18:30"]);
    expect(eren.phone.pendingAlarms()).toEqual([]);
  });

  it("quiet hours shift: a 06:00 deadline is poked before 23:00 the evening before", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.earlyParcelToEren);

    await world.advanceTo(at("2026-10-12T07:30:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@22:00",
      "SOUND@22:30",
      "FULL_SCREEN@22:45",
    ]);
    expect(world.violations).toEqual([]);
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("deadline edit regenerates the plan; old steps never ring", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T12:00:00"));

    expect(
      await selin.command(taskId, { type: "edit", deadline: at("2026-10-12T21:00:00") }),
    ).toEqual({ ok: true });
    expect(world.currentPlan(taskId)?.version).toBe(2);
    expect(world.push.to("eren").at(-1)).toMatchObject({ hint: "plan_changed", event: "edited" });

    await world.advanceTo(at("2026-10-12T20:50:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@20:00",
      "SOUND@20:30",
      "FULL_SCREEN@20:45",
    ]);
    await world.advanceTo(at("2026-10-12T21:01:00"));
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("full screen without the permission degrades to sound and is reported", async () => {
    const world = emptyWorld();
    const selin = await world.addPerson("Selin");
    const eren = await world.addPerson("Eren", {
      notifications: true,
      exactAlarm: true,
      fullScreen: false,
      channels: { poke_silent: true, poke_alarm: true },
    });
    await world.connect(selin, eren, "spouse", "spouse");
    expect(world.reachabilityOf("eren")).toBe("SOUND");

    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:46:00"));
    expect(eren.phone.ringsFor(taskId).at(-1)).toMatchObject({
      planned: "FULL_SCREEN",
      rang: "SOUND",
    });
    expect(world.eventsOf(taskId).map((e) => e.type)).toContain("permission_missing");
  });
});
