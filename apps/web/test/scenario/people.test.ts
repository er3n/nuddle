// Scenario list from docs/testing.md (Scenario tests): invites, logout and account deletion.
import { describe, expect, it } from "vitest";
import { at, emptyWorld, selinAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: people", () => {
  it("invite accepted: both are connected and Selin can write a task to Eren", async () => {
    const world = emptyWorld();
    const selin = await world.addPerson("Selin");
    const eren = await world.addPerson("Eren");

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
    const selin = await world.addPerson("Selin");
    const defne = await world.addPerson("Defne");

    const code = await selin.invite(defne.person.phone, "child", "Defne");
    const pushesBefore = world.push.to("selin").length;
    await defne.declineInvite(code);

    expect(world.invitesSeenBy("selin")).toEqual([{ code, status: "open" }]);
    expect(world.push.to("selin")).toHaveLength(pushesBefore);
    expect(world.edgesOf("selin")).toEqual([]);
    expect(await selin.write(TEXT.homeworkToDefne)).toEqual({ kind: "ask", question: "recipient" });
  });

  it("invite revoked: gone for both sides, and the code no longer works", async () => {
    const world = emptyWorld();
    const selin = await world.addPerson("Selin");
    const eren = await world.addPerson("Eren");

    const code = await selin.invite(eren.person.phone, "spouse", "Eren");
    const sent = await selin.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(sent.data?.sentInvites[0]?.createdAt).toBe(world.clock.now().toISOString());
    expect(world.invitesReceivedBy("eren")).toEqual([{ code, status: "open" }]);

    await expect(eren.revokeInvite(code)).rejects.toThrow(/DRT-4104/);
    await selin.revokeInvite(code);

    expect(world.invitesSeenBy("selin")).toEqual([{ code, status: "revoked" }]);
    expect(world.invitesReceivedBy("eren")).toEqual([{ code, status: "revoked" }]);
    await expect(eren.acceptInvite(code, "spouse")).rejects.toThrow(/DRT-4101/);
    expect(world.edgesOf("selin")).toEqual([]);
  });

  it("logout makes reachability NONE and pokes stop reaching that phone", async () => {
    const { world, selin, eren } = await selinAndEren();
    expect(world.reachabilityOf("eren")).toBe("FULL_SCREEN");

    await eren.logOut();
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

  it("quiet hours change: the open task is replanned to end before them, nothing rings inside", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    expect(world.currentPlan(taskId)?.steps.at(-1)?.plannedAt.getTime()).toBeGreaterThan(
      at("2026-10-12T18:00:00").getTime(),
    );

    await eren.updateSettings({ quietHours: { start: "18:00", end: "07:00" } });

    expect(world.person("eren").quietHours).toMatchObject({ start: "18:00", end: "07:00" });
    expect(world.task(taskId).planVersion).toBe(2);
    expect(world.task(taskId).status).toBe("SCHEDULED");
    const steps = world.currentPlan(taskId)?.steps ?? [];
    expect(steps.map((s) => s.level)).toEqual(["SILENT", "SOUND", "FULL_SCREEN", "HUMAN"]);
    expect(steps.every((s) => s.plannedAt.getTime() < at("2026-10-12T18:00:00").getTime())).toBe(
      true,
    );
    expect(eren.phone.received.at(-1)).toMatchObject({ taskId, hint: "plan_changed" });

    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(world.violations).toEqual([]);
    expect(eren.phone.ringsFor(taskId).map((r) => r.rang)).toEqual([
      "SILENT",
      "SOUND",
      "FULL_SCREEN",
    ]);
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("human channel switches: off for one connection or for my own tasks, Selin is never told", async () => {
    const { world, selin, eren } = await selinAndEren();
    const first = await writeTask(selin, TEXT.trashToEren);

    await selin.updateConnection(eren, { notifyMe: false });
    expect(world.edgesOf("selin").find((e) => e.fromPersonId === "selin")?.notifyMe).toBe(false);
    expect(world.currentPlan(first)?.steps.map((s) => s.level)).not.toContain("HUMAN");
    expect(world.edgesOf("selin").find((e) => e.fromPersonId === "eren")?.notifyMe).toBe(true);

    await selin.updateConnection(eren, { notifyMe: true });
    expect(world.currentPlan(first)?.steps.map((s) => s.level)).toContain("HUMAN");

    await eren.updateSettings({ useConnectionsForMyTasks: false });
    expect(world.currentPlan(first)?.steps.map((s) => s.level)).not.toContain("HUMAN");
    const second = await writeTask(selin, TEXT.photoToEren);
    expect(world.currentPlan(second)?.steps.map((s) => s.level)).not.toContain("HUMAN");

    await world.advanceTo(at("2026-10-12T19:01:00"));
    for (const id of [first, second]) {
      expect(world.eventsOf(id).map((e) => e.type)).not.toContain("human_notified");
    }
  });

  it("a setting that changes nothing replans nothing", async () => {
    const { world, selin, eren } = await selinAndEren();
    const taskId = await writeTask(selin, TEXT.trashToEren);
    await eren.updateSettings({ quietHours: { start: "23:00", end: "07:00" } });
    await selin.updateConnection(eren, { relationship: "roommate" });
    expect(world.task(taskId).planVersion).toBe(1);
  });

  it("relationship change: only my own side changes", async () => {
    const { world, selin, eren } = await selinAndEren();
    await eren.updateConnection(selin, { relationship: "roommate" });

    const mine = await eren.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(mine.data?.connections).toEqual([
      expect.objectContaining({ displayName: "Selin", relationship: "roommate", notifyMe: true }),
    ]);
    expect(mine.data?.connections[0]?.connectedAt).toBe(world.clock.now().toISOString());
    const theirs = await selin.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(theirs.data?.connections[0]?.relationship).toBe("spouse");
  });

  it("removing a connection cancels open tasks both ways without telling the other side", async () => {
    const { world, selin, eren } = await selinAndEren();
    const toEren = await writeTask(selin, TEXT.trashToEren);
    const toSelin = await writeTask(eren, TEXT.breadToSelin);
    const pushesBefore = world.push.to("selin").length;

    await eren.removeConnection(selin);

    expect(world.task(toEren).status).toBe("CANCELLED");
    expect(world.task(toSelin).status).toBe("CANCELLED");
    expect(world.eventsOf(toEren).at(-1)).toMatchObject({
      type: "cancelled",
      actorId: "eren",
      payload: { reason: "connection_removed" },
    });
    const newForSelin = world.push.to("selin").slice(pushesBefore);
    expect(newForSelin.map((p) => p.hint)).toEqual(["sync", "sync"]);
    expect(newForSelin.map((p) => p.hint)).not.toContain("task_cancelled");
    expect(world.edgesOf("selin").map((e) => e.status)).toEqual(["removed", "removed"]);
    const theirs = await selin.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(theirs.data?.connections[0]?.status).toBe("removed");
    expect(theirs.data?.connections[0]?.phone).toBeUndefined();

    // Neither can write to the other, and nothing rings for the cancelled tasks.
    expect(await selin.write(TEXT.trashToEren)).toEqual({ kind: "ask", question: "recipient" });
    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(eren.phone.ringsFor(toEren)).toEqual([]);
    expect(selin.phone.ringsFor(toSelin)).toEqual([]);

    // A new invite connects them again, counted from the new day.
    await world.connect(selin, eren, "spouse", "spouse");
    const again = await eren.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(again.data?.connections[0]).toMatchObject({
      status: "active",
      connectedAt: world.clock.now().toISOString(),
    });
  });
});
