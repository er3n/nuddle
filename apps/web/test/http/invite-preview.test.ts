// HTTP layer: GET /api/v1/invites/{code}/preview, the public invite preview (0b, 0e; ADR 0016).
import { v7 as uuidv7 } from "uuid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { startTestServer, type TestServer } from "../support/server";
import { signUp, type SignedUp } from "./helpers";

describe("http: invite preview", () => {
  let server: TestServer;
  let sibel: SignedUp;

  beforeAll(async () => {
    server = await startTestServer();
    sibel = await signUp(server, "Sibel");
  });
  afterAll(async () => {
    await server.close();
  });

  async function invite(prefilledName: string): Promise<{ inviteId: string; code: string }> {
    const r = await sibel.api.POST("/api/v1/commands/create_invite", {
      body: {
        idempotencyKey: uuidv7(),
        phone: "+905321110099",
        relationship: "spouse",
        prefilledName,
      },
    });
    if (!r.data) throw new Error("invite failed");
    return r.data;
  }

  it("shows a signed-out visitor who invites and the name they typed", async () => {
    const { code } = await invite("Eren");

    const r = await server
      .client()
      .GET("/api/v1/invites/{code}/preview", { params: { path: { code } } });

    expect(r.response.status).toBe(200);
    expect(r.data).toEqual({ inviterName: "Sibel", prefilledName: "Eren" });
  });

  it("answers not found for an unknown code and for an invite that is no longer open", async () => {
    const { inviteId, code } = await invite("Can");
    const revoked = await sibel.api.POST("/api/v1/commands/revoke_invite", {
      body: { idempotencyKey: uuidv7(), inviteId },
    });
    expect(revoked.response.status).toBe(200);

    for (const c of [code, "no-such-code"]) {
      const r = await server
        .client()
        .GET("/api/v1/invites/{code}/preview", { params: { path: { code: c } } });
      expect(r.response.status).toBe(404);
      expect(r.error).toMatchObject({ error: { code: "DRT-1004" } });
    }
  });
});
