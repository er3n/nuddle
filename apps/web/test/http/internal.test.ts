// HTTP layer: internal endpoints behind shared secrets (ADR 0003, 0007, 0011).
import { randomUUID } from "node:crypto";
import { Webhook } from "standardwebhooks";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { SMS_PHONE_DAILY_CAP, SMS_TOTAL_DAILY_CAP } from "../../src/domain/cost-caps";
import type { OtpSender } from "../../src/domain/ports";
import { HOUR } from "../../src/domain/time";
import { otpSends } from "../../src/infra/db/schema";
import { type FakeAuthGateway, type FakeClock, RecordingOtpSender } from "../fakes";
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
  const down = () => Promise.reject(new Error("provider down"));
  const broken: OtpSender = {
    whatsApp: false,
    sendWhatsApp: down,
    sendSms: down,
    priceOf: () => Promise.resolve(null),
  };

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

  // Last in this file: it moves the clock a day on.
  it("sends at most 5 codes to one phone in 24 hours; the API then refuses with DRT-1009 (ADR 0024)", async () => {
    const phone = "905320000009";
    const hook = { user: { id: "u9", phone }, sms: { otp: "111222" } };
    const before = otp.sent.length;
    for (let i = 0; i < SMS_PHONE_DAILY_CAP; i++) {
      const res = await server.app.request("/api/internal/otp-hook", signedHook(hook));
      expect(res.status).toBe(200);
    }
    const refused = await server.app.request("/api/internal/otp-hook", signedHook(hook));
    expect(refused.status).toBe(429);
    expect(await refused.json()).toEqual({ error: { http_code: 429, message: "too many codes" } });
    expect(otp.sent.length - before).toBe(SMS_PHONE_DAILY_CAP);

    // The API says so before asking Supabase Auth for a code at all.
    const asked = await server.client().POST("/api/v1/auth/otp", { body: { phone: `+${phone}` } });
    expect(asked.response.status).toBe(429);
    expect(asked.error?.error).toMatchObject({
      code: "DRT-1009",
      name: "otp_limit",
      details: { retryAt: "2026-10-12T17:00:00.000Z" },
    });
    expect((server.deps.auth as FakeAuthGateway).otpSentTo).not.toContain(`+${phone}`);

    (server.deps.clock as FakeClock).advance(24 * HOUR);
    const next = await server.app.request("/api/internal/otp-hook", signedHook(hook));
    expect(next.status).toBe(200);
    expect(otp.sent.length - before).toBe(SMS_PHONE_DAILY_CAP + 1);
  });

  it("sends no code to anyone once the total for 24 hours is reached", async () => {
    const recording = new RecordingOtpSender();
    const full = await startTestServer({ otp: recording });
    try {
      const now = full.deps.clock.now();
      await full.database.db.insert(otpSends).values(
        Array.from({ length: SMS_TOTAL_DAILY_CAP }, (_, i) => ({
          id: randomUUID(),
          phone: `+90532${String(i).padStart(7, "0")}`,
          sentAt: new Date(now.getTime() - HOUR),
        })),
      );
      const res = await full.app.request("/api/internal/otp-hook", signedHook(hookPayload));
      expect(res.status).toBe(429);
      expect(recording.sent).toEqual([]);
    } finally {
      await full.close();
    }
  });
});
