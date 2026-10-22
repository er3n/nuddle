// The sender's example photo (NUD-138, docs/domain.md, Example photo): attached after the task is
// written, synced to the recipient, never proof, and gone when the task closes or with the account.
import { describe, expect, it } from "vitest";
import { PHOTO_CLEANUP_RETRY_MS } from "../../src/domain/task-machine";
import { sibelAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: example photo", () => {
  it("Sibel attaches it; Eren's phone syncs it and fetches it through a signed URL", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    eren.phone.received.length = 0;

    const attached = await sibel.attachExamplePhoto(taskId);
    if (!attached.ok) throw new Error(attached.error);
    expect(attached.path.startsWith(`tasks/${taskId}/`)).toBe(true);
    expect(world.task(taskId).examplePhotoPath).toBe(attached.path);
    expect(world.eventsOf(taskId).map((e) => e.type)).toContain("example_photo_set");
    expect(eren.phone.received).toContainEqual(
      expect.objectContaining({ hint: "sync", taskId, event: "example_photo_set" }),
    );

    const synced = (await eren.phone.fullSync()).tasks.find((t) => t.id === taskId);
    expect(synced?.examplePhotoPath).toBe(attached.path);
    expect((await eren.exampleViewUrl(taskId)).data).toEqual({
      url: `https://storage.test/view/${attached.path}`,
      examplePhotoPath: attached.path,
    });
    expect((await sibel.exampleViewUrl(taskId)).data?.examplePhotoPath).toBe(attached.path);
  });

  it("is never proof: the task stays open, and a photo task still waits for Eren's own photo", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.photoToEren);
    const before = world.task(taskId);
    expect((await sibel.attachExamplePhoto(taskId)).ok).toBe(true);
    expect(world.task(taskId)).toMatchObject({
      status: "SCHEDULED",
      planVersion: before.planVersion,
    });
    expect(await eren.command(taskId, { type: "done" })).toEqual({
      ok: false,
      error: "wrong_finish_mode",
    });
    expect(await eren.sendPhoto(taskId)).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("AWAITING_APPROVAL");
  });

  it("only the sender attaches it, only to an open task of theirs, and only from the task's folder", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect(await eren.attachExamplePhoto(taskId)).toEqual({ ok: false, error: "not_sender" });
    const can = await world.addPerson("Can");
    expect(await can.attachExamplePhoto(taskId)).toEqual({ ok: false, error: "not_found" });
    expect((await can.exampleViewUrl(taskId)).error?.error.name).toBe("not_found");

    const outside = await sibel.setExamplePhoto(taskId, "tasks/other/1.jpg");
    expect(outside.error?.error.name).toBe("validation_failed");
    expect((await sibel.exampleViewUrl(taskId)).error?.error.name).toBe("not_found");

    // Undo within the toast: the upload that comes after it is refused, so the phone drops it.
    await sibel.command(taskId, { type: "cancel", note: null });
    expect(await sibel.attachExamplePhoto(taskId)).toEqual({ ok: false, error: "wrong_status" });
  });

  it("a second picture replaces the first and deletes its file", async () => {
    const { world, sibel } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    const first = await sibel.attachExamplePhoto(taskId);
    const second = await sibel.attachExamplePhoto(taskId);
    if (!first.ok || !second.ok) throw new Error("attach failed");
    expect(world.task(taskId).examplePhotoPath).toBe(second.path);
    expect(world.photos.deletedFiles).toEqual([first.path]);
  });

  it("is deleted when the task closes, retried later, and Eren's phone sees the path go", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect((await sibel.attachExamplePhoto(taskId)).ok).toBe(true);
    await eren.phone.sync();

    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });
    expect(world.task(taskId)).toMatchObject({ status: "DONE", examplePhotoPath: null });
    expect(world.photos.deleted).toContain(taskId);
    expect(world.photos.stored.has(taskId)).toBe(false);
    expect(world.pendingActions(taskId).map((a) => a.kind)).toEqual(["photo_cleanup"]);
    await world.advanceBy(PHOTO_CLEANUP_RETRY_MS);
    expect(world.pendingActions(taskId)).toEqual([]);

    const full = await eren.phone.fullSync();
    expect(full.tasks.find((t) => t.id === taskId)?.examplePhotoPath).toBeNull();
    expect((await eren.exampleViewUrl(taskId)).error?.error.name).toBe("not_found");
  });

  it("goes with the account: Sibel deletes hers, the open task is cancelled and its picture deleted", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    expect((await sibel.attachExamplePhoto(taskId)).ok).toBe(true);

    await sibel.deleteAccount();
    expect(world.task(taskId)).toMatchObject({ status: "CANCELLED", examplePhotoPath: null });
    expect(world.photos.deleted).toContain(taskId);
    expect(world.photos.stored.has(taskId)).toBe(false);
    const full = await eren.phone.fullSync();
    expect(full.tasks.find((t) => t.id === taskId)?.examplePhotoPath).toBeNull();
  });

  it("does not go on to the next occurrence of a repeating task (open-questions #64)", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.dailyMedicineToEren);
    expect((await sibel.attachExamplePhoto(taskId)).ok).toBe(true);
    expect(await eren.command(taskId, { type: "done" })).toEqual({ ok: true });
    const next = world
      .tasksWrittenBy("sibel")
      .find((t) => t.id !== taskId && t.status === "SCHEDULED");
    expect(next?.examplePhotoPath).toBeNull();
  });
});
