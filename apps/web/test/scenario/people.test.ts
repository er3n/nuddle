// Scenario list from docs/testing.md (Scenario tests): invites, logout and account deletion.
import { describe, expect, it } from "vitest";
import { at, emptyWorld, selinAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: people", () => {
  it("invite accepted: both are connected and Selin can write a task to Eren", async () => {
    const world = emptyWorld();
    const selin = world.addPerson("Selin");
    const eren = world.addPerson("Eren");

    // Before the invite is accepted, Eren is not a connection.
    expect(await selin.write(TEXT.trashToEren)).toEqual({ kind: "ask", question: "recipient" });

    const code = await selin.invite(eren.person.phone, "spouse", "Eren");
    await eren.acceptInvite(code, "spouse");

    expect(world.invitesSeenBy("selin")).toEqual([{ code, status: "accepted" }]);
    expect(world.push.to("selin").at(-1)).toMatchObject({ event: "invite_accepted" });
    expect(world.edgesOf("selin").map((e) => [e.fromPersonId, e.toPersonId, e.status])).toEqual([
      ["selin", "eren", "active"],
      ["eren", "selin", "active"],
    ]);
    expect((await selin.write(TEXT.trashToEren)).kind).toBe("created");
  });

  it("invite declined: stored, but invisible to the inviter", async () => {
    const world = emptyWorld();
    const selin = world.addPerson("Selin");
    const defne = world.addPerson("Defne");

    const code = await selin.invite(defne.person.phone, "child", "Defne");
    const pushesBefore = world.push.to("selin").length;
    defne.declineInvite(code);

    expect(world.invitesSeenBy("selin")).toEqual([{ code, status: "open" }]);
    expect(world.push.to("selin")).toHaveLength(pushesBefore);
    expect(world.edgesOf("selin")).toEqual([]);
    expect(await selin.write(TEXT.homeworkToDefne)).toEqual({ kind: "ask", question: "recipient" });
  });

  it("logout makes reachability NONE and pokes stop reaching that phone", async () => {
    const { world, selin, eren } = await selinAndEren();
    expect(world.reachabilityOf("eren")).toBe("FULL_SCREEN");

    eren.logOut();
    expect(world.reachabilityOf("eren")).toBe("NONE");

    const taskId = await writeTask(selin, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(eren.phone.received.filter((p) => p.taskId === taskId)).toEqual([]);
    expect(eren.phone.ringsFor(taskId)).toEqual([]);
    // The human channel still tells Selin, with the reachability she sees.
    const told = world.eventsOf(taskId).find((e) => e.type === "human_notified");
    expect(told?.payload).toMatchObject({ reachability: "NONE" });
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("account deletion cancels the other side's open tasks and tells them", async () => {
    const { world, selin, eren } = await selinAndEren();
    const toEren = await writeTask(selin, TEXT.trashToEren);
    const toSelin = await writeTask(eren, TEXT.breadToSelin);
    const doneTask = await writeTask(selin, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(doneTask);
    await selin.command(doneTask, { type: "approve" });

    await eren.deleteAccount();

    expect(world.task(toEren).status).toBe("CANCELLED");
    expect(world.task(toSelin).status).toBe("CANCELLED");
    expect(world.task(doneTask).status).toBe("DONE");
    const cancelledForSelin = world.push
      .to("selin")
      .filter((p) => p.hint === "task_cancelled")
      .map((p) => p.taskId);
    expect(cancelledForSelin.sort()).toEqual([toEren, toSelin].sort());
    expect(world.eventsOf(toEren).at(-1)).toMatchObject({
      type: "cancelled",
      actorId: "eren",
      payload: { reason: "account_deleted" },
    });
    expect(world.edgesOf("selin").every((e) => e.status === "removed")).toBe(true);
    expect(world.person("eren").deletedAt).not.toBeNull();
    expect(world.reachabilityOf("eren")).toBe("NONE");

    // Selin can no longer write to Eren.
    expect(await selin.write(TEXT.trashToEren)).toEqual({ kind: "ask", question: "recipient" });

    // Selin's own phone stops ringing for the cancelled task Eren wrote to her.
    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(selin.phone.ringsFor(toSelin)).toEqual([]);
  });
});
