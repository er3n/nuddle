// HTTP layer: looking at the photo that waits for approval (4f) and what sync says about it (4e, 4g).
import { v7 as uuidv7 } from "uuid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, type TestServer } from "../support/server";
import { connect, createTask, signUp, type SignedUp } from "./helpers";

describe("http: photos", () => {
  let server: TestServer;
  let selin: SignedUp;
  let eren: SignedUp;

  const syncTask = async (who: SignedUp, taskId: string) => {
    const r = await who.api.GET("/api/v1/sync", { params: { query: {} } });
    return r.data?.tasks.find((t) => t.id === taskId);
  };

  const sendPhoto = async (taskId: string) => {
    const upload = await eren.api.POST("/api/v1/photos/upload-url", { body: { taskId } });
    const photoPath = upload.data?.path ?? "";
    await eren.api.POST("/api/v1/commands/photo_sent", {
      body: { idempotencyKey: uuidv7(), taskId, photoPath },
    });
    return photoPath;
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

  it("the sender can look at the photo waiting for her approval, and sync says when it came", async () => {
    const taskId = await createTask(selin, eren, { finishMode: "photo" });
    const path = await sendPhoto(taskId);
    const now = server.deps.clock.now().toISOString();

    const view = await selin.api.POST("/api/v1/photos/view-url", { body: { taskId } });
    expect(view.data).toEqual({ url: `https://storage.test/view/${path}`, sentAt: now });
    expect((await syncTask(selin, taskId))?.photoSentAt).toBe(now);
    expect((await syncTask(eren, taskId))?.photoSentAt).toBe(now);
  });

  it("Tekrar iste: the note reaches the recipient, and there is no photo to look at until a new one", async () => {
    const taskId = await createTask(selin, eren, { finishMode: "photo" });
    await sendPhoto(taskId);
    await selin.api.POST("/api/v1/commands/re_request", {
      body: { idempotencyKey: uuidv7(), taskId, note: "1.500 lazımdı" },
    });

    expect((await syncTask(eren, taskId))?.reRequestNote).toBe("1.500 lazımdı");
    const view = await selin.api.POST("/api/v1/photos/view-url", { body: { taskId } });
    expect(view.error?.error.name).toBe("wrong_status");

    await sendPhoto(taskId);
    expect((await syncTask(eren, taskId))?.reRequestNote).toBeNull();
  });

  it("nobody else can look at the photo", async () => {
    const taskId = await createTask(selin, eren, { finishMode: "photo" });
    await sendPhoto(taskId);
    const defne = await signUp(server, "Defne");
    const view = await defne.api.POST("/api/v1/photos/view-url", { body: { taskId } });
    expect(view.error?.error.name).toBe("not_found");
  });
});
