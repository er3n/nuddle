// Scenario list from docs/testing.md (Scenario tests): task lifecycles across sender and recipient.
import { describe, expect, it } from "vitest";
import { MISSED_CARD_TTL_MS, PHOTO_CLEANUP_RETRY_MS } from "../../src/domain/task-machine";
import { visibleToRecipient } from "../../src/domain/tick";
import { MINUTE } from "../../src/domain/time";
import { at, emptyWorld, hhmm, sibelAndEren, TEXT, writeTask } from "./fixtures";

const rings = (r: { rang: string; at: Date }[]) => r.map((x) => `${x.rang}@${hhmm(x.at)}`);

describe("scenarios: task lifecycle", () => {
  it("photo task: unanswered pokes reach Sibel, photo stops poking, auto-approve after 3 hours", async () => {
    // The example in docs/testing.md.
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);

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
    expect(world.push.to("sibel").map((p) => p.event)).toContain("human_notified");

    await world.advanceTo(at("2026-10-12T08:58:00"));
    expect(await eren.sendPhoto(taskId)).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");
    expect(eren.phone.pendingAlarms()).toEqual([]);
    expect(world.push.to("sibel").map((p) => p.event)).toContain("photo_sent");

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

  it("at task: nothing rings before the moment, poking follows it, missed after its grace (ADR 0017)", async () => {
    const { world, sibel } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.waterSelfAt);
    expect(world.task(taskId)).toMatchObject({
      timing: "at",
      remindAt: at("2026-10-11T20:02:00"),
      deadline: at("2026-10-11T20:32:00"),
    });

    await world.advanceTo(at("2026-10-11T20:01:30"));
    expect(sibel.phone.ringsFor(taskId)).toEqual([]);

    await world.advanceTo(at("2026-10-11T20:33:00"));
    expect(rings(sibel.phone.ringsFor(taskId))).toEqual([
      "SOUND@20:02",
      "FULL_SCREEN@20:07",
      "FULL_SCREEN@20:22",
    ]);
    expect(world.task(taskId).status).toBe("MISSED");
    expect(world.violations).toEqual([]);
  });

  it("photo upload late (6e): taken before the deadline, it still counts after the task was missed", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    // The phone was offline until 09:20; the deadline was 09:00, so the task is missed by then.
    await world.advanceTo(at("2026-10-12T09:20:00"));
    expect(world.task(taskId).status).toBe("MISSED");

    // Taken after the deadline: still missed.
    expect(await eren.sendPhoto(taskId, at("2026-10-12T09:10:00"))).toEqual({
      ok: false,
      error: "wrong_status",
    });
    // Taken at 08:58: it counts.
    expect(await eren.sendPhoto(taskId, at("2026-10-12T08:58:00"))).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");
    expect(world.violations).toEqual([]);
  });

  it("snooze: 15 minutes, then 10 as the last one, then no more (open-questions#1)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);

    await world.advanceTo(at("2026-10-12T08:01:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@08:00"]);
    expect(eren.phone.snoozesUsed(taskId)).toBe(0);
    expect(await eren.command(taskId, { type: "snooze" })).toEqual({ ok: true });
    // The plan_changed push makes the phone sync: it knows the next snooze is the last one (NUD-36).
    expect(eren.phone.snoozesUsed(taskId)).toBe(1);

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
    expect(eren.phone.snoozesUsed(taskId)).toBe(2);

    await world.advanceTo(at("2026-10-12T08:28:00"));
    expect(await eren.command(taskId, { type: "snooze" })).toEqual({
      ok: false,
      error: "snooze_not_allowed",
    });
    expect(world.task(taskId).status).toBe("SCHEDULED");
    expect(world.violations).toEqual([]);
  });

  it("a greeting gets a one-line reply and creates nothing for anyone (NUD-52)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const pushesBefore = world.push.sent.length;
    expect(await sibel.write(TEXT.hello)).toEqual({
      kind: "chat",
      reply: "Merhaba! Kime ne hatırlatayım?",
    });
    expect(world.tasksWrittenBy("sibel")).toEqual([]);
    expect(eren.phone.pendingAlarms()).toEqual([]);
    expect(world.push.sent).toHaveLength(pushesBefore);
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
    expect(world.push.sent.every((p) => p.to === "eren")).toBe(true);
    // Done reaches Eren's other phones as a silent sync (NUD-59).
    expect(world.push.sent.filter((p) => p.event === "done").map((p) => p.hint)).toEqual(["sync"]);
  });

  it("task to another person, done: poking stops, the sender's phone only syncs (NUD-59)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);

    await world.advanceTo(at("2026-10-12T18:31:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00", "SOUND@18:30"]);
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });

    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(world.task(taskId).status).toBe("DONE");
    expect(eren.phone.ringsFor(taskId)).toHaveLength(2);
    // Silent syncs keep Sibel's list current; nothing tells her with a notification.
    expect(
      world.push
        .to("sibel")
        .filter((p) => p.taskId === taskId)
        .map((p) => `${p.hint}:${p.event}`),
    ).toEqual(["sync:created", "sync:done"]);
    expect(world.eventsOf(taskId).map((e) => e.type)).toContain("done");
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("photo + approve", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(taskId);

    await world.advanceBy(10 * MINUTE);
    expect(await sibel.command(taskId, { type: "approve" })).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("DONE");
    expect(world.push.to("eren").at(-1)).toMatchObject({ taskId, event: "approved" });
    expect(world.photos.deleted).toContain(taskId);
    // Only the later cleanup is left (NUD-25); it deletes again, then nothing is pending.
    expect(world.pendingActions(taskId).map((a) => a.kind)).toEqual(["photo_cleanup"]);
    await world.advanceBy(PHOTO_CLEANUP_RETRY_MS);
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("photo + re-request: poking resumes where it stopped, then a second photo is approved", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(taskId);

    await world.advanceTo(at("2026-10-12T08:35:00"));
    expect(await sibel.command(taskId, { type: "re_request", note: "Fiş görünmüyor" })).toEqual({
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
    await sibel.command(taskId, { type: "approve" });
    expect(world.task(taskId).status).toBe("DONE");
    expect(world.violations).toEqual([]);
  });

  it("can't today with a note: the sender is told and can give a new time", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:31:00"));

    expect(
      await eren.command(taskId, { type: "cant_today", note: "Bugün eve geç geliyorum" }),
    ).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("DROPPED");
    expect(world.push.to("sibel").at(-1)).toMatchObject({ taskId, event: "dropped" });
    expect(world.eventsOf(taskId).find((e) => e.type === "dropped")?.payload).toEqual({
      note: "Bugün eve geç geliyorum",
    });

    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(eren.phone.ringsFor(taskId)).toHaveLength(2);
    expect(world.task(taskId).status).toBe("DROPPED");

    // "Yeni zaman ver"
    expect(
      await sibel.command(taskId, { type: "new_deadline", deadline: at("2026-10-13T19:00:00") }),
    ).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("SCHEDULED");
    await world.advanceTo(at("2026-10-13T18:01:00"));
    expect(rings(eren.phone.ringsFor(taskId)).at(-1)).toBe("SILENT@18:00");
  });

  it("missed, then 'Sonradan yaptım'", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);

    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(world.task(taskId).status).toBe("MISSED");
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@18:00",
      "SOUND@18:30",
      "FULL_SCREEN@18:45",
    ]);
    expect(world.push.to("sibel").map((p) => p.event)).toEqual(
      expect.arrayContaining(["human_notified", "missed"]),
    );
    expect(world.push.to("eren").at(-1)).toMatchObject({ taskId, event: "missed" });

    expect(await eren.command(taskId, { type: "did_it_later" })).toEqual({ ok: true });
    expect(world.task(taskId)).toMatchObject({ status: "DONE", late: true });
  });

  it("missed card: Kaldır takes it off Eren's list at once, Sibel still sees it missed (NUD-49)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(eren.phone.inList.get(taskId)).toBe(true);

    expect(await sibel.removeMissed(taskId)).toEqual({ ok: false, error: "not_recipient" });
    expect(await eren.removeMissed(taskId)).toEqual({ ok: true });
    await eren.phone.sync();
    expect(eren.phone.inList.get(taskId)).toBe(false);
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("the sender's tick (NUD-68): reached, then seen, each once, and Sibel's phone has both", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect(await sibel.acknowledge(taskId, false)).toEqual({ ok: false, error: "not_recipient" });

    expect(await eren.acknowledge(taskId, false)).toEqual({ ok: true });
    expect(world.push.to("sibel").at(-1)).toMatchObject({
      taskId,
      hint: "sync",
      event: "delivered",
    });
    expect(await eren.acknowledge(taskId, true)).toEqual({ ok: true });
    expect(await eren.acknowledge(taskId, true)).toEqual({ ok: true });

    const logged = world.eventsOf(taskId).map((e) => e.type);
    expect(logged.filter((t) => t === "delivered" || t === "seen")).toEqual(["delivered", "seen"]);
    await sibel.phone.sync();
    const sibelSaw = sibel.phone.events.filter((e) => e.taskId === taskId).map((e) => e.type);
    expect(sibelSaw).toEqual(expect.arrayContaining(["delivered", "seen"]));
    expect(world.task(taskId).status).toBe("SCHEDULED");
  });

  it("missed, then 'Süreyi uzat' (5d): poking starts again for the new deadline", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(world.task(taskId).status).toBe("MISSED");

    // Sibel's phone has what the Kaçırıldı card says: how many pokes rang and that she was told.
    const sibelSaw = sibel.phone.events.filter((e) => e.taskId === taskId).map((e) => e.type);
    expect(sibelSaw.filter((t) => t === "poke_fired")).toHaveLength(3);
    expect(sibelSaw).toEqual(expect.arrayContaining(["human_notified", "missed"]));

    expect(
      await sibel.command(taskId, { type: "new_deadline", deadline: at("2026-10-13T19:00:00") }),
    ).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("SCHEDULED");
    expect(world.push.to("eren").at(-1)).toMatchObject({ taskId, hint: "plan_changed" });
    await world.advanceTo(at("2026-10-13T19:01:00"));
    expect(rings(eren.phone.ringsFor(taskId)).slice(3)).toEqual([
      "SILENT@18:00",
      "SOUND@18:30",
      "FULL_SCREEN@18:45",
    ]);
    expect(world.task(taskId).status).toBe("MISSED");
    expect(world.violations).toEqual([]);
  });

  it("missed, then cancel (5d İptal et): the card goes, nothing rings", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:01:00"));

    expect(await sibel.command(taskId, { type: "cancel", note: null })).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("CANCELLED");
    expect(world.pendingActions(taskId)).toEqual([]);
    // Too late for "Sonradan yaptım".
    expect(await eren.command(taskId, { type: "did_it_later" })).toMatchObject({ ok: false });
  });

  it("dropped with a note, then 'Ben hallederim' (5d): closed for good (open-questions#8)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:46:00"));
    expect(await eren.command(taskId, { type: "cant_today", note: "Vaktim olmadı" })).toEqual({
      ok: true,
    });
    expect(sibel.phone.events).toContainEqual({
      taskId,
      type: "dropped",
      payload: { note: "Vaktim olmadı" },
    });

    expect(await sibel.command(taskId, { type: "sender_handles" })).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("CANCELLED");
    expect(world.eventsOf(taskId).at(-1)).toMatchObject({
      type: "cancelled",
      payload: { reason: "sender_handles" },
    });
    // Eren's phone learns why, so it does not show a "Sibel iptal etti" row for a task he gave up himself.
    expect(eren.phone.events.at(-1)).toMatchObject({
      taskId,
      type: "cancelled",
      payload: { reason: "sender_handles" },
    });
    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(rings(eren.phone.ringsFor(taskId))).toEqual([
      "SILENT@18:00",
      "SOUND@18:30",
      "FULL_SCREEN@18:45",
    ]);
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("human channel (7a): only while the task is open", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:50:00"));
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });

    await world.advanceTo(at("2026-10-12T19:30:00"));
    expect(world.push.to("sibel").map((p) => p.event)).not.toContain("human_notified");
    expect(sibel.phone.events.map((e) => e.type)).not.toContain("human_notified");
  });

  it("a missed card leaves the recipient's list after one day", async () => {
    const { world, sibel } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
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
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:10:00"));

    expect(await sibel.command(taskId, { type: "cancel", note: "Ben hallettim" })).toEqual({
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
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:10:00"));

    eren.phone.pushesDelayed = true;
    await sibel.command(taskId, { type: "cancel", note: null });
    expect(eren.phone.pendingAlarms()).not.toEqual([]);
    eren.phone.pushesDelayed = false;

    await world.advanceTo(at("2026-10-12T20:00:00"));
    // Accepted MVP edge case (ADR 0005): SOUND rings once, its report is answered with a cancel.
    expect(rings(eren.phone.ringsFor(taskId))).toEqual(["SILENT@18:00", "SOUND@18:30"]);
    expect(eren.phone.pendingAlarms()).toEqual([]);
  });

  it("quiet hours shift: a 06:00 deadline is poked before 23:00 the evening before", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.earlyParcelToEren);

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
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T12:00:00"));

    expect(
      await sibel.command(taskId, { type: "edit", deadline: at("2026-10-12T21:00:00") }),
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
    const sibel = await world.addPerson("Sibel");
    const eren = await world.addPerson("Eren", {
      notifications: true,
      exactAlarm: true,
      fullScreen: false,
      channels: { poke_silent: true, poke_alarm: true },
    });
    await world.connect(sibel, eren, "spouse", "spouse");
    expect(world.reachabilityOf("eren")).toBe("SOUND");

    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T18:46:00"));
    expect(eren.phone.ringsFor(taskId).at(-1)).toMatchObject({
      planned: "FULL_SCREEN",
      rang: "SOUND",
    });
    expect(world.eventsOf(taskId).map((e) => e.type)).toContain("permission_missing");
  });

  it("a photo delete that fails when the task closes is retried until Storage answers (NUD-25)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(taskId);

    world.photos.failing = true;
    expect(await sibel.command(taskId, { type: "approve" })).toEqual({ ok: true });
    expect(world.photos.stored.has(taskId)).toBe(true);

    // Still down at the first retry: it is planned again.
    await world.advanceBy(PHOTO_CLEANUP_RETRY_MS);
    expect(world.photos.stored.has(taskId)).toBe(true);
    expect(world.pendingActions(taskId).map((a) => a.kind)).toEqual(["photo_cleanup"]);

    world.photos.failing = false;
    await world.advanceBy(PHOTO_CLEANUP_RETRY_MS);
    expect(world.photos.stored.has(taskId)).toBe(false);
    expect(world.pendingActions(taskId)).toEqual([]);
  });

  it("a cleanup timer leaves a late photo alone: the task opened again for approval (6e)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T09:01:00"));
    expect(world.task(taskId).status).toBe("MISSED");

    expect(await eren.sendPhoto(taskId, at("2026-10-12T08:58:00"))).toEqual({ ok: true });
    await world.advanceBy(PHOTO_CLEANUP_RETRY_MS);
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");
    expect(world.photos.stored.has(taskId)).toBe(true);
  });
});
