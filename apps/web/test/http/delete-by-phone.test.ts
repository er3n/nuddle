// HTTP layer: deleting an account by phone for an email request (NUD-88), behind the tick secret.
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, TICK_SECRET, type TestServer } from "../support/server";
import { connect, createTask, signUp, type SignedUp } from "./helpers";

describe("http: delete an account by phone", () => {
  let server: TestServer;
  let sibel: SignedUp;
  let eren: SignedUp;
  let taskId: string;

  const ask = (body: unknown, secret = TICK_SECRET) =>
    server.app.request("/api/internal/delete-account", {
      method: "POST",
      headers: { Authorization: `Bearer ${secret}`, "content-type": "application/json" },
      body: JSON.stringify(body),
    });

  beforeAll(async () => {
    server = await startTestServer();
    sibel = await signUp(server, "Sibel");
    eren = await signUp(server, "Eren");
    await connect(sibel, eren);
    taskId = await createTask(sibel, eren);
  });
  afterAll(async () => {
    await server.close();
  });

  it("needs the tick secret and an E.164 number", async () => {
    expect((await ask({ phone: eren.phone }, "nope")).status).toBe(401);
    expect((await ask({ phone: "05320000000" })).status).toBe(400);
  });

  it("answers null for a number with no account", async () => {
    const res = await ask({ phone: "+905559999999" });
    expect(await res.json()).toEqual({ account: null, deleted: false });
  });

  it("without confirm only says what would be deleted", async () => {
    const res = await ask({ phone: eren.phone });
    expect(await res.json()).toEqual({
      account: {
        personId: eren.id,
        displayName: "Eren",
        tasks: 1,
        openTasks: 1,
        connections: 1,
      },
      deleted: false,
    });
    expect((await eren.api.GET("/api/v1/sync", { params: { query: {} } })).response.status).toBe(
      200,
    );
  });

  it("with confirm deletes the account as the app does, and then finds nothing", async () => {
    const res = await ask({ phone: eren.phone, confirm: true });
    expect(await res.json()).toMatchObject({ account: { personId: eren.id }, deleted: true });

    const sync = await sibel.api.GET("/api/v1/sync", { params: { query: {} } });
    expect(sync.data?.tasks.find((t) => t.id === taskId)?.status).toBe("CANCELLED");
    expect((await eren.api.GET("/api/v1/sync", { params: { query: {} } })).response.status).toBe(
      403,
    );
    expect(await (await ask({ phone: eren.phone })).json()).toEqual({
      account: null,
      deleted: false,
    });
  });
});
