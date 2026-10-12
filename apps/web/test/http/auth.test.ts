// HTTP layer (docs/testing.md, Layers): auth, request ids, validation and error codes.
import { v7 as uuidv7 } from "uuid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, type TestServer } from "../support/server";
import type { FakeClock } from "../fakes";
import { signUp } from "./helpers";

describe("http: auth and errors", () => {
  let server: TestServer;

  beforeAll(async () => {
    server = await startTestServer();
  });
  afterAll(async () => {
    await server.close();
  });

  it("rejects a missing or foreign token with DRT-1002 and a request id", async () => {
    const res = await server.app.request("/api/v1/sync");
    expect(res.status).toBe(401);
    const body = (await res.json()) as { error: { code: string; requestId: string } };
    expect(body.error.code).toBe("DRT-1002");
    expect(body.error.requestId).toBe(res.headers.get("x-request-id"));

    const other = await startTestServer();
    try {
      const forged = await other.tokenFor("00000000-0000-4000-8000-000000000009", "+905320000009");
      const r = await server.client(forged).GET("/api/v1/sync", { params: { query: {} } });
      expect(r.response.status).toBe(401);
    } finally {
      await other.close();
    }
  });

  it("answers DRT-1003 until the person registers", async () => {
    const token = await server.tokenFor("00000000-0000-4000-8000-000000000008", "+905320000008");
    const r = await server.client(token).GET("/api/v1/sync", { params: { query: {} } });
    expect(r.response.status).toBe(403);
    expect(r.error?.error.code).toBe("DRT-1003");
  });

  it("validates bodies with DRT-1001: an idempotency key must be a UUIDv7", async () => {
    const selin = await signUp(server, "Selin");
    const r = await selin.api.POST("/api/v1/commands/delete_account", {
      body: { idempotencyKey: "00000000-0000-4000-8000-000000000001" },
    });
    expect(r.response.status).toBe(400);
    expect(r.error?.error).toMatchObject({ code: "DRT-1001", name: "validation_failed" });
  });

  it("unknown routes are DRT-1004", async () => {
    const res = await server.app.request("/api/v1/nope");
    expect(res.status).toBe(401);
    const unauth = await server.app.request("/api/nope");
    expect(unauth.status).toBe(404);
    expect(((await unauth.json()) as { error: { code: string } }).error.code).toBe("DRT-1004");
  });

  it("signs in through the API: wrong code, then a session, then registered after register", async () => {
    const api = server.client();
    expect((await api.POST("/api/v1/auth/otp", { body: { phone: "+905320000005" } })).data).toEqual(
      {
        ok: true,
      },
    );

    const wrong = await api.POST("/api/v1/auth/verify", {
      body: { phone: "+905320000005", code: "000000" },
    });
    expect(wrong.response.status).toBe(401);
    expect(wrong.error?.error.code).toBe("DRT-1006");

    const session = await api.POST("/api/v1/auth/verify", {
      body: { phone: "+905320000005", code: "123456" },
    });
    expect(session.data).toMatchObject({ registered: false });
    if (!session.data) throw new Error("no session");

    const me = server.client(session.data.accessToken);
    const reg = await me.POST("/api/v1/commands/register", {
      body: { idempotencyKey: uuidv7(), displayName: "Can", timeZone: "Europe/Istanbul" },
    });
    expect(reg.data).toMatchObject({ id: session.data.personId, phone: "+905320000005" });

    const refreshed = await api.POST("/api/v1/auth/refresh", {
      body: { refreshToken: session.data.refreshToken },
    });
    expect(refreshed.data).toMatchObject({ personId: session.data.personId, registered: true });
    const bad = await api.POST("/api/v1/auth/refresh", { body: { refreshToken: "nope" } });
    expect(bad.error?.error.code).toBe("DRT-1006");
  });

  it("counts wrong codes (6c): two left, one left, then a 15 minute wait for codes and new ones", async () => {
    const api = server.client();
    const phone = "+905320000006";
    const verify = (code: string) => api.POST("/api/v1/auth/verify", { body: { phone, code } });

    expect((await verify("000000")).error?.error).toMatchObject({
      code: "DRT-1006",
      details: { remainingAttempts: 2 },
    });
    expect((await verify("000000")).error?.error.details).toEqual({ remainingAttempts: 1 });
    const locked = await verify("000000");
    expect(locked.response.status).toBe(429);
    expect(locked.error?.error).toMatchObject({
      code: "DRT-1008",
      details: { retryAt: "2026-10-11T17:15:00.000Z" },
    });

    // Even the right code and a new code wait.
    expect((await verify("123456")).error?.error.code).toBe("DRT-1008");
    const resend = await api.POST("/api/v1/auth/otp", { body: { phone } });
    expect(resend.error?.error.code).toBe("DRT-1008");

    (server.deps.clock as FakeClock).advance(15 * 60_000);
    expect((await verify("123456")).data).toMatchObject({ registered: false });
    // A sign-in clears the count.
    expect((await verify("000000")).error?.error.details).toEqual({ remainingAttempts: 2 });
  });
});
