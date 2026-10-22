// Ara or Mesaj at on the full screen poke (NUD-128, docs/domain.md, Call or message from the poke).
import { describe, expect, it } from "vitest";
import { at, sibelAndEren, TEXT, writeTask } from "./fixtures";

async function withAyse() {
  const base = await sibelAndEren();
  const ayse = await base.world.addPerson("Ayşe");
  await base.world.connect(base.sibel, ayse, "parent", "child");
  return { ...base, ayse };
}

describe("scenarios: the contact of a task (NUD-128)", () => {
  it("a task to self about calling a connection carries her; the phone gets it, Bitti still closes it", async () => {
    const { world, sibel, ayse } = await withAyse();
    const taskId = await writeTask(sibel, TEXT.callMomTomorrowSelf);

    expect(world.task(taskId)).toMatchObject({
      recipientId: "sibel",
      contact: { personId: "ayşe", action: "call" },
    });
    expect(sibel.phone.contacts.get(taskId)).toEqual({ personId: ayse.uuid, action: "call" });

    await world.advanceTo(at("2026-10-12T18:50:00"));
    expect(world.task(taskId).status).toBe("SCHEDULED");
    expect(await sibel.command(taskId, { type: "done" })).toEqual({ ok: true });
    expect(world.task(taskId).status).toBe("DONE");
  });

  it("the preview keeps the contact in its draft and the server keeps it on create", async () => {
    const { world, sibel, ayse } = await withAyse();
    const r = await sibel.writeRaw(TEXT.callMomSelf);
    expect(r).toMatchObject({
      kind: "ask",
      question: "deadline",
      draft: { contact: { personId: ayse.uuid, action: "call" } },
    });

    const taskId = await sibel.createFromPreview({
      recipientId: sibel.uuid,
      title: "Annemi ara",
      deadline: at("2026-10-12T19:00:00").toISOString(),
      contact: r.draft?.contact ?? null,
    });
    expect(world.task(taskId).contact).toEqual({ personId: "ayşe", action: "call" });
  });

  it("a contact the recipient does not know is dropped, even when the phone sends one", async () => {
    const { world, sibel, eren, ayse } = await withAyse();
    const stranger = await world.addPerson("Defne");

    const fromPreview = await sibel.createFromPreview({
      recipientId: sibel.uuid,
      title: "Ara",
      deadline: at("2026-10-12T19:00:00").toISOString(),
      contact: { personId: stranger.uuid, action: "call" },
    });
    expect(world.task(fromPreview).contact).toBeNull();

    // Eren is not connected to Ayşe: his phone has no number for her.
    const notKnown = await writeTask(sibel, TEXT.messageAyseEren);
    expect(world.task(notKnown).contact).toBeNull();

    await world.connect(eren, ayse, "parent", "child");
    const known = await writeTask(sibel, TEXT.messageAyseEren);
    expect(world.task(known).contact).toEqual({ personId: "ayşe", action: "message" });
    expect(eren.phone.contacts.get(known)).toEqual({ personId: ayse.uuid, action: "message" });
  });

  it("'eren beni arasın': the sender is the contact; a name that is no connection gives none", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const callMe = await writeTask(sibel, TEXT.callMeEren);
    expect(world.task(callMe)).toMatchObject({
      recipientId: "eren",
      contact: { personId: "sibel", action: "call" },
    });
    expect(eren.phone.contacts.get(callMe)).toEqual({ personId: sibel.uuid, action: "call" });

    const teacher = await writeTask(sibel, TEXT.callTeacherSelf);
    expect(world.task(teacher).contact).toBeNull();
  });

  it("Kime changed: the contact goes along only when the new recipient knows the person", async () => {
    const { world, sibel, eren } = await withAyse();
    const taskId = await writeTask(sibel, TEXT.callMomTomorrowSelf);

    const r = await sibel.reassign(taskId, eren);
    if (!r.ok) throw new Error(r.error);
    expect(world.task(r.taskId).contact).toBeNull();
  });

  it("the contact deleting her account: the purge clears it and the task stays", async () => {
    const { world, sibel, ayse } = await withAyse();
    const taskId = await writeTask(sibel, TEXT.callMomTomorrowSelf);

    await ayse.deleteAccount();
    await world.advanceTo(at("2026-10-19T20:01:00"));

    expect(await world.rowsHolding(ayse)).toEqual({});
    expect(world.task(taskId)).toMatchObject({ senderId: "sibel", contact: null });
  });
});
