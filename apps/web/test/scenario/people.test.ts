// Scenario list from docs/testing.md (Scenario tests): invites, logout and account deletion.
import { describe, expect, it } from "vitest";
import { aPerson } from "../builders";
import { at, emptyWorld, sibelAndEren, TEXT, writeTask } from "./fixtures";

describe("scenarios: people", () => {
  it("invite accepted: both are connected and Sibel can write a task to Eren", async () => {
    const world = emptyWorld();
    const sibel = await world.addPerson("Sibel");
    const eren = await world.addPerson("Eren");

    // Before the invite is accepted, Eren is not a connection.
    expect(await sibel.write(TEXT.trashToEren)).toEqual({ kind: "ask", question: "recipient" });

    const code = await sibel.invite(eren.person.phone, "spouse", "Eren");
    await eren.acceptInvite(code, "spouse");

    expect(world.invitesSeenBy("sibel")).toEqual([{ code, status: "accepted" }]);
    expect(world.push.to("sibel").at(-1)).toMatchObject({ event: "invite_accepted" });
    expect(world.edgesOf("sibel").map((e) => [e.fromPersonId, e.toPersonId, e.status])).toEqual([
      ["sibel", "eren", "active"],
      ["eren", "sibel", "active"],
    ]);
    expect((await sibel.write(TEXT.trashToEren)).kind).toBe("created");
  });

  it("invite declined: stored, but invisible to the inviter", async () => {
    const world = emptyWorld();
    const sibel = await world.addPerson("Sibel");
    const defne = await world.addPerson("Defne");

    const code = await sibel.invite(defne.person.phone, "child", "Defne");
    const pushesBefore = world.push.to("sibel").length;
    await defne.declineInvite(code);

    expect(world.invitesSeenBy("sibel")).toEqual([{ code, status: "open" }]);
    expect(world.push.to("sibel")).toHaveLength(pushesBefore);
    expect(world.edgesOf("sibel")).toEqual([]);
    expect(await sibel.write(TEXT.homeworkToDefne)).toEqual({ kind: "ask", question: "recipient" });
  });

  it("invite revoked: gone for both sides, and the code no longer works", async () => {
    const world = emptyWorld();
    const sibel = await world.addPerson("Sibel");
    const eren = await world.addPerson("Eren");

    const code = await sibel.invite(eren.person.phone, "spouse", "Eren");
    const sent = await sibel.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(sent.data?.sentInvites[0]?.createdAt).toBe(world.clock.now().toISOString());
    expect(world.invitesReceivedBy("eren")).toEqual([{ code, status: "open" }]);

    await expect(eren.revokeInvite(code)).rejects.toThrow(/DRT-4104/);
    await sibel.revokeInvite(code);

    expect(world.invitesSeenBy("sibel")).toEqual([{ code, status: "revoked" }]);
    expect(world.invitesReceivedBy("eren")).toEqual([{ code, status: "revoked" }]);
    await expect(eren.acceptInvite(code, "spouse")).rejects.toThrow(/DRT-4101/);
    expect(world.edgesOf("sibel")).toEqual([]);
  });

  it("logout makes reachability NONE and pokes stop reaching that phone", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    expect(world.reachabilityOf("eren")).toBe("FULL_SCREEN");

    await eren.logOut();
    expect(world.reachabilityOf("eren")).toBe("NONE");

    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await world.advanceTo(at("2026-10-12T19:01:00"));
    expect(eren.phone.received.filter((p) => p.taskId === taskId)).toEqual([]);
    expect(eren.phone.ringsFor(taskId)).toEqual([]);
    // The human channel still tells Sibel, with the reachability she sees.
    const told = world.eventsOf(taskId).find((e) => e.type === "human_notified");
    expect(told?.payload).toMatchObject({ reachability: "NONE" });
    expect(world.task(taskId).status).toBe("MISSED");
  });

  it("account deletion cancels the other side's open tasks and tells them", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const toEren = await writeTask(sibel, TEXT.trashToEren);
    const toSibel = await writeTask(eren, TEXT.breadToSibel);
    const doneTask = await writeTask(sibel, TEXT.photoToEren);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(doneTask);
    await sibel.command(doneTask, { type: "approve" });

    await eren.deleteAccount();

    expect(world.task(toEren).status).toBe("CANCELLED");
    expect(world.task(toSibel).status).toBe("CANCELLED");
    expect(world.task(doneTask).status).toBe("DONE");
    const cancelledForSibel = world.push
      .to("sibel")
      .filter((p) => p.hint === "task_cancelled")
      .map((p) => p.taskId);
    expect(cancelledForSibel.sort()).toEqual([toEren, toSibel].sort());
    expect(world.eventsOf(toEren).at(-1)).toMatchObject({
      type: "cancelled",
      actorId: "eren",
      payload: { reason: "account_deleted" },
    });
    expect(world.edgesOf("sibel").every((e) => e.status === "removed")).toBe(true);
    expect(world.person("eren").deletedAt).not.toBeNull();
    expect(world.reachabilityOf("eren")).toBe("NONE");

    // Sibel can no longer write to Eren.
    expect(await sibel.write(TEXT.trashToEren)).toEqual({ kind: "ask", question: "recipient" });

    // Sibel's own phone stops ringing for the cancelled task Eren wrote to her.
    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(sibel.phone.ringsFor(toSibel)).toEqual([]);
  });

  it("account deletion erases the number and name at once and every task a week later", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    await writeTask(sibel, TEXT.trashToEren);
    await writeTask(eren, TEXT.breadToSibel);
    const done = await writeTask(sibel, TEXT.photoToEren);
    const sibelsOwn = await writeTask(sibel, TEXT.selfSport);
    await world.advanceTo(at("2026-10-12T08:31:00"));
    await eren.sendPhoto(done);
    await sibel.command(done, { type: "approve" });
    await eren.invite(aPerson("Defne").phone, "child", "Defne");

    await eren.deleteAccount();

    expect(world.person("eren")).toMatchObject({ phone: `deleted:${eren.uuid}`, displayName: "" });
    expect(await world.deletedAuthUsers()).toEqual([eren.uuid]);
    // The tasks stay for a week so Sibel's phone syncs the cancels; the number and invites go now.
    const now = await world.rowsHolding(eren);
    expect(Object.keys(now).sort()).toEqual(
      ["edges", "idempotencyKeys", "people", "pokeSteps", "taskEvents", "tasks"].sort(),
    );
    expect(world.pendingActions().filter((a) => a.kind === "account_purge")).toHaveLength(1);

    await world.advanceTo(at("2026-10-19T08:32:00"));

    expect(await world.rowsHolding(eren)).toEqual({});
    expect(world.edgesOf("sibel")).toEqual([]);
    // Sibel's own task is not Eren's to delete.
    expect(world.task(sibelsOwn).senderId).toBe("sibel");
  });

  it("the same number signs up again right after deleting and starts empty", async () => {
    const { world, eren } = await sibelAndEren();
    await writeTask(eren, TEXT.breadToSibel);
    await eren.deleteAccount();

    const again = await world.addPerson("Eren");

    expect(again.uuid).not.toBe(eren.uuid);
    expect(Object.keys(await world.rowsHolding(again)).sort()).toEqual(
      ["devices", "idempotencyKeys", "people"].sort(),
    );
    expect(await again.write(TEXT.breadToSibel)).toEqual({ kind: "ask", question: "recipient" });
  });

  it("an auth user Supabase failed to delete goes with the purge", async () => {
    const { world, eren } = await sibelAndEren();
    await world.failNextAuthDelete();

    await eren.deleteAccount();
    expect(await world.deletedAuthUsers()).toEqual([]);

    await world.advanceTo(at("2026-10-19T20:01:00"));
    expect(await world.deletedAuthUsers()).toEqual([eren.uuid]);
    expect(await world.rowsHolding(eren)).toEqual({});
  });

  it("quiet hours change: the open task is replanned to end before them, nothing rings inside", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
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

  it("human channel switches: off for one connection or for my own tasks, Sibel is never told", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const first = await writeTask(sibel, TEXT.trashToEren);

    await sibel.updateConnection(eren, { notifyMe: false });
    expect(world.edgesOf("sibel").find((e) => e.fromPersonId === "sibel")?.notifyMe).toBe(false);
    expect(world.currentPlan(first)?.steps.map((s) => s.level)).not.toContain("HUMAN");
    expect(world.edgesOf("sibel").find((e) => e.fromPersonId === "eren")?.notifyMe).toBe(true);

    await sibel.updateConnection(eren, { notifyMe: true });
    expect(world.currentPlan(first)?.steps.map((s) => s.level)).toContain("HUMAN");

    await eren.updateSettings({ useConnectionsForMyTasks: false });
    expect(world.currentPlan(first)?.steps.map((s) => s.level)).not.toContain("HUMAN");
    const second = await writeTask(sibel, TEXT.photoToEren);
    expect(world.currentPlan(second)?.steps.map((s) => s.level)).not.toContain("HUMAN");

    await world.advanceTo(at("2026-10-12T19:01:00"));
    for (const id of [first, second]) {
      expect(world.eventsOf(id).map((e) => e.type)).not.toContain("human_notified");
    }
  });

  it("a setting that changes nothing replans nothing", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const taskId = await writeTask(sibel, TEXT.trashToEren);
    await eren.updateSettings({ quietHours: { start: "23:00", end: "07:00" } });
    await sibel.updateConnection(eren, { relationship: "roommate" });
    expect(world.task(taskId).planVersion).toBe(1);
  });

  it("relationship change: only my own side changes", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    await eren.updateConnection(sibel, { relationship: "roommate" });

    const mine = await eren.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(mine.data?.connections).toEqual([
      expect.objectContaining({ displayName: "Sibel", relationship: "roommate", notifyMe: true }),
    ]);
    expect(mine.data?.connections[0]?.connectedAt).toBe(world.clock.now().toISOString());
    const theirs = await sibel.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(theirs.data?.connections[0]?.relationship).toBe("spouse");
  });

  it("removing a connection cancels open tasks both ways without telling the other side", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const toEren = await writeTask(sibel, TEXT.trashToEren);
    const toSibel = await writeTask(eren, TEXT.breadToSibel);
    const pushesBefore = world.push.to("sibel").length;

    await eren.removeConnection(sibel);

    expect(world.task(toEren).status).toBe("CANCELLED");
    expect(world.task(toSibel).status).toBe("CANCELLED");
    expect(world.eventsOf(toEren).at(-1)).toMatchObject({
      type: "cancelled",
      actorId: "eren",
      payload: { reason: "connection_removed" },
    });
    const newForSibel = world.push.to("sibel").slice(pushesBefore);
    expect(newForSibel.map((p) => p.hint)).toEqual(["sync", "sync"]);
    expect(newForSibel.map((p) => p.hint)).not.toContain("task_cancelled");
    expect(world.edgesOf("sibel").map((e) => e.status)).toEqual(["removed", "removed"]);
    const theirs = await sibel.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(theirs.data?.connections[0]?.status).toBe("removed");
    expect(theirs.data?.connections[0]?.phone).toBeUndefined();

    // Neither can write to the other, and nothing rings for the cancelled tasks.
    expect(await sibel.write(TEXT.trashToEren)).toEqual({ kind: "ask", question: "recipient" });
    await world.advanceTo(at("2026-10-12T20:00:00"));
    expect(eren.phone.ringsFor(toEren)).toEqual([]);
    expect(sibel.phone.ringsFor(toSibel)).toEqual([]);

    // A new invite connects them again, counted from the new day.
    await world.connect(sibel, eren, "spouse", "spouse");
    const again = await eren.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(again.data?.connections[0]).toMatchObject({
      status: "active",
      connectedAt: world.clock.now().toISOString(),
    });
  });
});
