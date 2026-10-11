// HTTP layer: idempotent commands (ADR 0006) and domain errors mapped to DRT codes.
import { eq } from "drizzle-orm";
import { v7 as uuidv7 } from "uuid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { tasks } from "../../src/infra/db/schema";
import { startTestServer, type TestServer } from "../support/server";
import { connect, createTask, signUp, type SignedUp } from "./helpers";

describe("http: commands", () => {
  let server: TestServer;
  let selin: SignedUp;
  let eren: SignedUp;

  beforeAll(async () => {
    server = await startTestServer();
    selin = await signUp(server, "Selin");
    eren = await signUp(server, "Eren");
    await connect(selin, eren);
  });
  afterAll(async () => {
    await server.close();
  });

  it("a replayed command returns the first result and changes nothing", async () => {
    const body = {
      idempotencyKey: uuidv7(),
      recipientId: eren.id,
      title: "Ekmek al",
      detail: null,
      deadline: "2026-10-12T19:00:00+03:00",
      finishMode: "done" as const,
      rawText: null,
    };
    const first = await selin.api.POST("/api/v1/commands/create_task", { body });
    const replay = await selin.api.POST("/api/v1/commands/create_task", { body });
    expect(replay.data).toEqual(first.data);
    const rows = await server.database.db.select().from(tasks).where(eq(tasks.title, "Ekmek al"));
    expect(rows).toHaveLength(1);

    // The same key for another command is a client bug.
    const reused = await selin.api.POST("/api/v1/commands/delete_account", {
      body: { idempotencyKey: body.idempotencyKey },
    });
    expect(reused.response.status).toBe(422);
    expect(reused.error?.error.code).toBe("DRT-1005");
  });

  it("maps domain errors: wrong status 409, wrong side 403, not your task 404", async () => {
    const taskId = await createTask(selin, eren);

    const approve = await selin.api.POST("/api/v1/commands/approve", {
      body: { idempotencyKey: uuidv7(), taskId },
    });
    expect(approve.response.status).toBe(409);
    expect(approve.error?.error).toMatchObject({ code: "DRT-4001", name: "wrong_status" });

    const cancelByRecipient = await eren.api.POST("/api/v1/commands/cancel", {
      body: { idempotencyKey: uuidv7(), taskId, note: null },
    });
    expect(cancelByRecipient.response.status).toBe(403);
    expect(cancelByRecipient.error?.error.code).toBe("DRT-4002");

    const defne = await signUp(server, "Defne");
    const stranger = await defne.api.POST("/api/v1/commands/done", {
      body: { idempotencyKey: uuidv7(), taskId },
    });
    expect(stranger.response.status).toBe(404);
  });

  it("a failed command stores nothing, so a retry with the same key runs again", async () => {
    const taskId = await createTask(selin, eren, { finishMode: "photo" });
    const key = uuidv7();
    const early = await eren.api.POST("/api/v1/commands/done", {
      body: { idempotencyKey: key, taskId },
    });
    expect(early.error?.error.code).toBe("DRT-4004");
    const fallback = await eren.api.POST("/api/v1/commands/done", {
      body: { idempotencyKey: key, taskId, cameraUnavailable: true },
    });
    expect(fallback.data).toMatchObject({ taskId, status: "DONE" });
  });

  it("only accepts a photo path uploaded for the same task", async () => {
    const taskId = await createTask(selin, eren, { finishMode: "photo" });
    const sender = await selin.api.POST("/api/v1/photos/upload-url", { body: { taskId } });
    expect(sender.error?.error.code).toBe("DRT-4003");

    const upload = await eren.api.POST("/api/v1/photos/upload-url", { body: { taskId } });
    expect(upload.data?.path).toMatch(new RegExp(`^tasks/${taskId}/`));

    const foreign = await eren.api.POST("/api/v1/commands/photo_sent", {
      body: { idempotencyKey: uuidv7(), taskId, photoPath: "tasks/other/1.jpg" },
    });
    expect(foreign.error?.error.code).toBe("DRT-1001");
  });

  it("a task cannot go to someone who is not connected", async () => {
    const can = await signUp(server, "Can");
    const r = await selin.api.POST("/api/v1/commands/create_task", {
      body: {
        idempotencyKey: uuidv7(),
        recipientId: can.id,
        title: "Ödevini bitir",
        detail: null,
        deadline: "2026-10-12T19:00:00+03:00",
        finishMode: "done",
        rawText: null,
      },
    });
    expect(r.error?.error).toMatchObject({ code: "DRT-4006", name: "not_connected" });
  });
});
