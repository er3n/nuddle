// HTTP layer: internal endpoints behind shared secrets (ADR 0003, 0007, 0011).
import { Webhook } from "standardwebhooks";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { OtpSender } from "../../src/domain/ports";
import { RecordingOtpSender } from "../fakes";
import { SMS_HOOK_SECRET, startTestServer, TICK_SECRET, type TestServer } from "../support/server";

function signedHook(payload: object, secret = SMS_HOOK_SECRET): RequestInit {
  const body = JSON.stringify(payload);
  const id = "msg_1";
  const timestamp = new Date();
  const signature = new Webhook(secret.replace(/^v1,whsec_/, "")).sign(id, timestamp, body);
  return {
    method: "POST",
    body,
    headers: {
      "content-type": "application/json",
      "webhook-id": id,
      "webhook-timestamp": String(Math.floor(timestamp.getTime() / 1000)),
      "webhook-signature": signature,
    },
  };
}

const hookPayload = { user: { id: "u1", phone: "905320000002" }, sms: { otp: "482913" } };

describe("http: internal endpoints", () => {
  let server: TestServer;
  let failing: TestServer;
  const otp = new RecordingOtpSender();
  const broken: OtpSender = { send: () => Promise.reject(new Error("provider down")) };

  beforeAll(async () => {
    server = await startTestServer({ otp });
    failing = await startTestServer({ otp: broken });
  });
  afterAll(async () => {
    await server.close();
    await failing.close();
  });

  it("tick needs the shared secret", async () => {
    const without = await server.app.request("/api/internal/tick", { method: "POST" });
    expect(without.status).toBe(401);
    const wrong = await server.app.request("/api/internal/tick", {
      method: "POST",
      headers: { Authorization: "Bearer nope" },
    });
    expect(wrong.status).toBe(401);
    const right = await server.app.request("/api/internal/tick", {
      method: "POST",
      headers: { Authorization: `Bearer ${TICK_SECRET}` },
    });
    expect(right.status).toBe(200);
    expect(await right.json()).toEqual({ handled: 0 });
  });

  it("the Send SMS hook sends the code to the E.164 number when the signature is valid", async () => {
    const res = await server.app.request("/api/internal/otp-hook", signedHook(hookPayload));
    expect(res.status).toBe(200);
    expect(otp.sent).toEqual([{ phone: "+905320000002", code: "482913" }]);
  });

  it("the hook rejects a wrong signature and sends nothing", async () => {
    const before = otp.sent.length;
    const other = `v1,whsec_${Buffer.from("someone-else").toString("base64")}`;
    const res = await server.app.request("/api/internal/otp-hook", signedHook(hookPayload, other));
    expect(res.status).toBe(401);
    expect(otp.sent).toHaveLength(before);
  });

  it("a provider failure is reported in the shape Supabase Auth expects", async () => {
    const res = await failing.app.request("/api/internal/otp-hook", signedHook(hookPayload));
    expect(res.status).toBe(500);
    expect(await res.json()).toEqual({
      error: { http_code: 500, message: "could not send the code" },
    });
  });
});
