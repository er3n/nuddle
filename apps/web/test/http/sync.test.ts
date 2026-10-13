// HTTP layer: what GET /api/v1/sync shows to whom (ADR 0006; docs/domain.md, Poke plan and
// Reachability).
import { v7 as uuidv7 } from "uuid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, TICK_SECRET, type TestServer } from "../support/server";
import { connect, createTask, signUp, type SignedUp } from "./helpers";
import { FakeClock } from "../fakes";

describe("http: sync", () => {
  let server: TestServer;
  let selin: SignedUp;
  let eren: SignedUp;

  const syncOf = async (who: SignedUp, cursor?: string) => {
    const r = await who.api.GET("/api/v1/sync", {
      params: { query: cursor === undefined ? {} : { cursor } },
    });
    if (!r.data) throw new Error(`sync failed: ${JSON.stringify(r.error)}`);
    return r.data;
  };

  beforeAll(async () => {
    server = await startTestServer();
    selin = await signUp(server, "Selin");
    eren = await signUp(server, "Eren");
    await connect(selin, eren);
  });
  afterAll(async () => {
    await server.close();
  });

  it("gives the plan only to the recipient's device", async () => {
    const taskId = await createTask(selin, eren);
    const forEren = await syncOf(eren);
    const forSelin = await syncOf(selin);

    expect(forEren.plans.find((p) => p.taskId === taskId)?.steps.length).toBeGreaterThan(0);
    expect(forSelin.plans).toEqual([]);
    expect(forSelin.events.filter((e) => e.taskId === taskId).map((e) => e.type)).toEqual([
      "created",
    ]);
    expect(forSelin.tasks.find((t) => t.id === taskId)).toMatchObject({
      status: "SCHEDULED",
      senderId: selin.id,
      recipientId: eren.id,
    });
    // The HUMAN step is server side; the device only gets what it rings.
    expect(forEren.plans.flatMap((p) => p.steps.map((s) => s.level))).not.toContain("HUMAN");
  });

  it("tells when a task was written and how to reach a connection", async () => {
    const taskId = await createTask(selin, eren);
    const forEren = await syncOf(eren);
    expect(forEren.tasks.find((t) => t.id === taskId)?.createdAt).toBe(
      server.deps.clock.now().toISOString(),
    );
    expect(forEren.connections.find((c) => c.personId === selin.id)?.phone).toBe(selin.phone);
  });

  it("stops giving the phone of someone who deleted their account", async () => {
    // Own people: the other tests count Selin's connections.
    const ali = await signUp(server, "Ali");
    const zeynep = await signUp(server, "Zeynep");
    await connect(ali, zeynep);
    const before = await syncOf(ali);
    expect(before.connections.find((c) => c.personId === zeynep.id)?.phone).toBe(zeynep.phone);

    await zeynep.api.POST("/api/v1/commands/delete_account", {
      body: { idempotencyKey: uuidv7() },
    });
    const after = await syncOf(ali, before.cursor);
    expect(after.connections.find((c) => c.personId === zeynep.id)).toMatchObject({
      status: "removed",
    });
    expect(after.connections.find((c) => c.personId === zeynep.id)?.phone).toBeUndefined();
  });

  it("returns only what changed after the cursor", async () => {
    const before = await syncOf(eren);
    expect((await syncOf(eren, before.cursor)).tasks).toEqual([]);

    const taskId = await createTask(selin, eren);
    const delta = await syncOf(eren, before.cursor);
    expect(delta.tasks.map((t) => t.id)).toEqual([taskId]);

    await eren.api.POST("/api/v1/commands/done", { body: { idempotencyKey: uuidv7(), taskId } });
    const after = await syncOf(eren, delta.cursor);
    expect(after.tasks).toMatchObject([{ id: taskId, status: "DONE" }]);
    // Poking stopped: a newer plan version with no steps.
    expect(after.plans).toMatchObject([{ taskId, version: 2, steps: [] }]);
  });

  it("a declined invite looks open to the inviter and does not even move their feed", async () => {
    const defne = await signUp(server, "Defne");
    const invite = await selin.api.POST("/api/v1/commands/create_invite", {
      body: {
        idempotencyKey: uuidv7(),
        phone: defne.phone,
        relationship: "child",
        prefilledName: "Defne",
      },
    });
    const code = invite.data?.code ?? "";
    const seen = await syncOf(selin);
    expect(seen.sentInvites.find((i) => i.code === code)?.status).toBe("open");
    expect((await syncOf(defne)).receivedInvites).toMatchObject([{ code, inviterName: "Selin" }]);

    await defne.api.POST("/api/v1/commands/decline_invite", {
      body: { idempotencyKey: uuidv7(), code },
    });
    expect((await syncOf(selin, seen.cursor)).sentInvites).toEqual([]);
    expect((await syncOf(selin)).sentInvites.find((i) => i.code === code)?.status).toBe("open");
  });

  it("shows each connection's reachability and updates it when a device changes", async () => {
    const r1 = await syncOf(selin);
    expect(r1.connections).toMatchObject([
      {
        personId: eren.id,
        displayName: "Eren",
        relationship: "spouse",
        reachability: "FULL_SCREEN",
      },
    ]);

    const changed = await eren.api.POST("/api/v1/devices", {
      body: {
        deviceId: eren.deviceId,
        fid: `fid-${eren.id}`,
        platform: "android",
        appVersion: "test",
        permissions: {
          notifications: true,
          exactAlarm: true,
          fullScreen: false,
          channels: { poke_silent: true, poke_alarm: true },
        },
      },
    });
    expect(changed.data).toEqual({ deviceId: eren.deviceId, reachability: "SOUND" });
    expect((await syncOf(selin, r1.cursor)).connections).toMatchObject([{ reachability: "SOUND" }]);

    await eren.api.POST("/api/v1/commands/log_out", {
      body: { idempotencyKey: uuidv7(), deviceId: eren.deviceId },
    });
    expect((await syncOf(selin)).connections).toMatchObject([{ reachability: "NONE" }]);
  });

  it("a missed card leaves the recipient's list one day later", async () => {
    const clock = server.deps.clock as FakeClock;
    const taskId = await createTask(selin, eren, { deadline: "2026-10-12T19:00:00+03:00" });
    const tick = () =>
      server.app.request("/api/internal/tick", {
        method: "POST",
        headers: { Authorization: `Bearer ${TICK_SECRET}` },
      });

    clock.set(new Date("2026-10-12T19:00:00+03:00"));
    await tick();
    expect((await syncOf(eren)).tasks.find((t) => t.id === taskId)).toMatchObject({
      status: "MISSED",
      inRecipientList: true,
    });
    const cursor = (await syncOf(eren)).cursor;

    clock.set(new Date("2026-10-13T19:00:00+03:00"));
    await tick();
    // missed_expiry touches the task so a delta sync carries the new visibility.
    expect((await syncOf(eren, cursor)).tasks.filter((t) => t.id === taskId)).toMatchObject([
      { status: "MISSED", inRecipientList: false },
    ]);
  });
});
