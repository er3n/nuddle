// HTTP layer: GET /api/v1/health (docs/bootstrap-plan.md, Stage 7) against a real database.
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { connectDb } from "../../src/infra/db/client";
import { startTestServer, type TestServer } from "../support/server";

describe("http: health", () => {
  let server: TestServer;
  let unreachable: TestServer;
  let closeUnreachable: () => Promise<void>;

  beforeAll(async () => {
    server = await startTestServer();
    // Nothing listens on port 1: every query fails at once.
    const dead = connectDb("postgresql://postgres:postgres@127.0.0.1:1/postgres", { max: 1 });
    closeUnreachable = dead.close;
    unreachable = await startTestServer({ db: dead.db });
  });
  afterAll(async () => {
    await server.close();
    await unreachable.close();
    await closeUnreachable();
  });

  it("answers without a token with the version and a reachable database", async () => {
    const r = await server.client().GET("/api/v1/health");
    expect(r.response.status).toBe(200);
    expect(r.data).toEqual({ status: "ok", version: "test", database: "ok" });
    expect(r.response.headers.get("x-request-id")).toBeTruthy();
  });

  it("answers 503 when the database is down", async () => {
    const r = await unreachable.app.request("/api/v1/health");
    expect(r.status).toBe(503);
    expect(await r.json()).toEqual({ status: "error", version: "test", database: "down" });
  });
});
