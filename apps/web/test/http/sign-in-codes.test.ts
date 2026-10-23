// HTTP layer: sign-in codes over WhatsApp with the SMS fallback (ADR 0029). The Send SMS hook's
// channel, Twilio's status callback, `auth/otp` answering the channel and `auth/otp/sms`.
import { randomUUID } from "node:crypto";
import { eq } from "drizzle-orm";
import { afterEach, describe, expect, it } from "vitest";
import { DAY, HOUR, MINUTE } from "../../src/domain/time";
import { otpSends } from "../../src/infra/db/schema";
import { twilioSignature } from "../../src/infra/otp/twilio";
import { type FakeAuthGateway, type FakeClock, RecordingOtpSender } from "../fakes";
import {
  signedSmsHook,
  startTestServer,
  twilioCallback,
  TICK_SECRET,
  TWILIO_AUTH_TOKEN,
  TWILIO_STATUS_URL,
  type TestServer,
} from "../support/server";

const ERENS_PHONE = "+905320000002";

let servers: TestServer[] = [];
afterEach(async () => {
  for (const s of servers) await s.close();
  servers = [];
});

/** The API with the WhatsApp sender on, where Supabase Auth calls the hook for a real number. */
async function serverWith(whatsApp: boolean) {
  const otp = new RecordingOtpSender({ whatsApp });
  const server = await startTestServer({ otp });
  servers.push(server);
  (server.deps.auth as FakeAuthGateway).onSendOtp = async (phone) => {
    const res = await server.app.request("/api/internal/otp-hook", signedSmsHook(phone, "123456"));
    if (res.status !== 200) throw new Error(`hook answered ${String(res.status)}`);
  };
  const clock = server.deps.clock as FakeClock;
  const rows = () =>
    server.database.db.select().from(otpSends).where(eq(otpSends.phone, ERENS_PHONE));
  const askCode = (phone = ERENS_PHONE) =>
    server.client().POST("/api/v1/auth/otp", { body: { phone } });
  const askSms = (phone = ERENS_PHONE) =>
    server.client().POST("/api/v1/auth/otp/sms", { body: { phone } });
  const status = (params: Record<string, string>, signature?: string) =>
    server.app.request("/api/internal/twilio-status", twilioCallback(params, signature));
  const tick = () =>
    server.app.request("/api/internal/tick", {
      method: "POST",
      headers: { Authorization: `Bearer ${TICK_SECRET}` },
    });
  return { server, otp, clock, rows, askCode, askSms, status, tick };
}

describe("http: the Send SMS hook's channel", () => {
  it("sends the WhatsApp template in Turkish to a +90 number and holds the code encrypted", async () => {
    const { otp, rows, server } = await serverWith(true);
    const res = await server.app.request(
      "/api/internal/otp-hook",
      signedSmsHook(ERENS_PHONE, "482913"),
    );
    expect(res.status).toBe(200);
    expect(otp.whatsAppSent).toMatchObject([
      { phone: ERENS_PHONE, code: "482913", language: "tr" },
    ]);
    expect(otp.sent).toEqual([]);
    const [row] = await rows();
    expect(row).toMatchObject({
      channel: "whatsapp",
      twilioSid: otp.whatsAppSent[0]?.sid,
      status: null,
      fallbackAt: null,
    });
    expect(row?.codeCiphertext).toEqual(expect.any(String));
    expect(row?.codeCiphertext).not.toContain("482913");
    // The 12 s fallback was handed to waitUntil, not awaited by the hook.
    expect(server.delay.waits).toEqual([12_000]);
  });

  it("sends SMS only, holding nothing, while the WhatsApp sender is not configured", async () => {
    const { otp, rows, server } = await serverWith(false);
    await server.app.request("/api/internal/otp-hook", signedSmsHook(ERENS_PHONE, "482913"));
    expect(otp.sent).toEqual([{ phone: ERENS_PHONE, code: "482913" }]);
    expect(await rows()).toMatchObject([
      { channel: "sms", twilioSid: otp.smsSids[0], codeCiphertext: null },
    ]);
    expect(server.delay.waits).toEqual([]);
  });

  it("sends the SMS at once when Twilio refuses the WhatsApp request itself, without remembering it", async () => {
    const { otp, rows, server } = await serverWith(true);
    otp.failNextWhatsApp = true;
    const res = await server.app.request(
      "/api/internal/otp-hook",
      signedSmsHook(ERENS_PHONE, "482913"),
    );
    expect(res.status).toBe(200);
    expect(otp.sent).toEqual([{ phone: ERENS_PHONE, code: "482913" }]);
    expect(await rows()).toMatchObject([{ channel: "sms", status: null }]);

    await server.app.request("/api/internal/otp-hook", signedSmsHook(ERENS_PHONE, "111222"));
    expect(otp.whatsAppSent.map((m) => m.code)).toEqual(["111222"]);
  });

  it("deletes rows older than 31 days and keeps a day old row past the cap window", async () => {
    const { rows, server, clock } = await serverWith(false);
    const now = clock.now();
    await server.database.db.insert(otpSends).values([
      { id: randomUUID(), phone: ERENS_PHONE, sentAt: new Date(now.getTime() - 32 * DAY) },
      { id: randomUUID(), phone: ERENS_PHONE, sentAt: new Date(now.getTime() - 25 * HOUR) },
    ]);
    await server.app.request("/api/internal/otp-hook", signedSmsHook(ERENS_PHONE, "482913"));
    expect((await rows()).map((r) => r.sentAt.getTime()).sort()).toEqual([
      now.getTime() - 25 * HOUR,
      now.getTime(),
    ]);
  });
});

describe("http: POST /api/internal/twilio-status", () => {
  async function withWhatsAppCode() {
    const ctx = await serverWith(true);
    await ctx.server.app.request("/api/internal/otp-hook", signedSmsHook(ERENS_PHONE, "482913"));
    const sid = ctx.otp.whatsAppSent[0]?.sid ?? "";
    return { ...ctx, sid };
  }

  it("refuses a wrong, missing or tampered signature and changes nothing", async () => {
    const { status, rows, sid, otp } = await withWhatsAppCode();
    const params = { MessageSid: sid, MessageStatus: "failed", ErrorCode: "63003" };

    expect((await status(params, "bm90LWEtc2lnbmF0dXJl")).status).toBe(403);
    expect((await status(params, "")).status).toBe(403);
    // Signed for one status, sent with another.
    const signedForDelivered = twilioSignature(TWILIO_AUTH_TOKEN, TWILIO_STATUS_URL, {
      ...params,
      MessageStatus: "delivered",
    });
    expect((await status(params, signedForDelivered)).status).toBe(403);

    expect(await rows()).toMatchObject([{ status: null, errorCode: null, fallbackAt: null }]);
    expect(otp.sent).toEqual([]);
  });

  it("checks the signature against the configured public URL, not the request's host", async () => {
    const { server, sid } = await withWhatsAppCode();
    const params = { MessageSid: sid, MessageStatus: "delivered" };
    const forHost = twilioSignature(
      TWILIO_AUTH_TOKEN,
      "http://localhost/api/internal/twilio-status",
      params,
    );
    const res = await server.app.request(
      "http://localhost/api/internal/twilio-status",
      twilioCallback(params, forHost),
    );
    expect(res.status).toBe(403);
  });

  it("refuses every callback while Twilio is not configured", async () => {
    const server = await startTestServer();
    servers.push(server);
    const off = await startTestServer({
      otpDelivery: { ...server.deps.otpDelivery, twilioAuthToken: null },
    });
    servers.push(off);
    const res = await off.app.request(
      "/api/internal/twilio-status",
      twilioCallback({ MessageSid: "MM1", MessageStatus: "delivered" }),
    );
    expect(res.status).toBe(403);
  });

  it("records the status; duplicates and late callbacks never move it back from delivered or read", async () => {
    const { status, rows, sid, otp } = await withWhatsAppCode();
    for (const s of ["sent", "delivered", "delivered", "sent", "queued", "failed"]) {
      expect((await status({ MessageSid: sid, MessageStatus: s })).status).toBe(204);
    }
    expect(await rows()).toMatchObject([{ status: "delivered", fallbackAt: null }]);
    await status({ MessageSid: sid, MessageStatus: "read" });
    await status({ MessageSid: sid, MessageStatus: "delivered" });
    expect(await rows()).toMatchObject([{ status: "read" }]);
    expect(otp.sent).toEqual([]);
  });

  it("sends the SMS of the same code at once on failed, once however often it is told", async () => {
    const { status, rows, sid, otp } = await withWhatsAppCode();
    const failed = { MessageSid: sid, MessageStatus: "failed", ErrorCode: "63003" };
    await status(failed);
    await status(failed);
    await status({ MessageSid: sid, MessageStatus: "undelivered", ErrorCode: "63003" });
    expect(otp.sent).toEqual([{ phone: ERENS_PHONE, code: "482913" }]);
    const [row] = await rows();
    expect(row).toMatchObject({
      status: "failed",
      errorCode: "63003",
      fallbackSid: otp.smsSids[0],
      codeCiphertext: null,
    });
    expect(row?.fallbackAt).toEqual(expect.any(Date));
  });

  it("adds each message's price once, on its first final status", async () => {
    const { status, rows, sid, otp } = await withWhatsAppCode();
    await status({ MessageSid: sid, MessageStatus: "sent" });
    expect(await rows()).toMatchObject([{ costUsd: null }]);
    await status({ MessageSid: sid, MessageStatus: "undelivered", Price: "-0.0080" });
    await status({ MessageSid: sid, MessageStatus: "undelivered", Price: "-0.0080" });
    expect(await rows()).toMatchObject([{ costUsd: 0.008 }]);

    // The SMS fallback's price comes from Twilio's API when the callback has none.
    const smsSid = otp.smsSids[0] ?? "";
    otp.prices.set(smsSid, 0.0305);
    await status({ MessageSid: smsSid, MessageStatus: "sent" });
    await status({ MessageSid: smsSid, MessageStatus: "delivered" });
    await status({ MessageSid: smsSid, MessageStatus: "delivered" });
    const [row] = await rows();
    expect(row).toMatchObject({ fallbackStatus: "delivered", status: "undelivered" });
    expect(row?.costUsd).toBeCloseTo(0.0385, 6);
  });

  it("keeps the cost null while Twilio has not priced the message", async () => {
    const { status, rows, sid } = await withWhatsAppCode();
    await status({ MessageSid: sid, MessageStatus: "delivered" });
    expect(await rows()).toMatchObject([{ costUsd: null }]);
  });

  it("answers an unknown message without changing anything", async () => {
    const { status, rows } = await withWhatsAppCode();
    expect((await status({ MessageSid: "MMunknown", MessageStatus: "failed" })).status).toBe(204);
    expect(await rows()).toMatchObject([{ status: null }]);
  });
});

describe("http: POST /api/v1/auth/otp answers the channel", () => {
  it("whatsapp when the hook sent the template", async () => {
    const { askCode } = await serverWith(true);
    const r = await askCode();
    expect(r.data).toEqual({ ok: true, channel: "whatsapp" });
  });

  it("sms when the hook sent an SMS", async () => {
    const { askCode } = await serverWith(false);
    expect((await askCode()).data).toEqual({ ok: true, channel: "sms" });
  });

  it("sms for a test number, which never reaches the hook", async () => {
    const server = await startTestServer({ otp: new RecordingOtpSender({ whatsApp: true }) });
    servers.push(server);
    const r = await server.client().POST("/api/v1/auth/otp", { body: { phone: ERENS_PHONE } });
    expect(r.data).toEqual({ ok: true, channel: "sms" });
  });
});

describe("http: POST /api/v1/auth/otp/sms", () => {
  it("sends the code already sent by WhatsApp again by SMS, once", async () => {
    const { askCode, askSms, otp, rows } = await serverWith(true);
    await askCode();
    const first = await askSms();
    expect(first.data).toEqual({ ok: true, channel: "sms" });
    expect(otp.sent).toEqual([{ phone: ERENS_PHONE, code: "123456" }]);

    const again = await askSms();
    expect(again.response.status).toBe(409);
    expect(again.error?.error).toMatchObject({ code: "DRT-1011", name: "otp_resend_unavailable" });
    expect(otp.sent).toHaveLength(1);
    // No new code: one row, counted once toward the caps.
    expect(await rows()).toHaveLength(1);
  });

  it("works even after WhatsApp said delivered: the person may not see it", async () => {
    const { askCode, askSms, otp, server } = await serverWith(true);
    await askCode();
    await server.app.request(
      "/api/internal/twilio-status",
      twilioCallback({ MessageSid: otp.whatsAppSent[0]?.sid ?? "", MessageStatus: "delivered" }),
    );
    expect((await askSms()).data).toEqual({ ok: true, channel: "sms" });
    expect(otp.sent).toHaveLength(1);
  });

  it("is unavailable with no code, with an SMS code, and after the code's 10 minutes", async () => {
    const sms = await serverWith(false);
    expect((await sms.askSms()).error?.error.code).toBe("DRT-1011");
    await sms.askCode();
    expect((await sms.askSms()).error?.error.code).toBe("DRT-1011");
    expect(sms.otp.sent).toHaveLength(1);

    const wa = await serverWith(true);
    await wa.askCode();
    wa.clock.advance(10 * MINUTE);
    expect((await wa.askSms()).error?.error.code).toBe("DRT-1011");
    expect(wa.otp.sent).toEqual([]);
  });

  it("is unavailable once the automatic fallback sent the SMS", async () => {
    const { askCode, askSms, otp, server, clock } = await serverWith(true);
    await askCode();
    clock.advance(12_000);
    server.delay.elapse();
    await server.work.settle();
    expect(otp.sent).toHaveLength(1);
    expect((await askSms()).error?.error.code).toBe("DRT-1011");
    expect(otp.sent).toHaveLength(1);
  });

  it("asks for a Turkish mobile number like auth/otp", async () => {
    const { askSms } = await serverWith(true);
    for (const phone of ["+12025550123", "+902125550123"]) {
      const r = await askSms(phone);
      expect(r.response.status).toBe(400);
      expect(r.error?.error.code).toBe("DRT-1001");
    }
  });
});

describe("http: held codes", () => {
  it("a correct code clears the phone's held code", async () => {
    const { askCode, rows, server } = await serverWith(true);
    await askCode();
    expect((await rows())[0]?.codeCiphertext).not.toBeNull();
    const r = await server
      .client()
      .POST("/api/v1/auth/verify", { body: { phone: ERENS_PHONE, code: "123456" } });
    expect(r.response.status).toBe(200);
    expect((await rows())[0]?.codeCiphertext).toBeNull();
  });

  it("the tick clears a code held past 10 minutes", async () => {
    const { askCode, rows, tick, clock, server } = await serverWith(true);
    await askCode();
    // Delivered, so the tick sends no SMS; the code is still held until it expires.
    await server.app.request(
      "/api/internal/twilio-status",
      twilioCallback({
        MessageSid: (await rows())[0]?.twilioSid ?? "",
        MessageStatus: "delivered",
      }),
    );
    clock.advance(10 * MINUTE - 1);
    await tick();
    expect((await rows())[0]?.codeCiphertext).not.toBeNull();
    clock.advance(1);
    await tick();
    expect((await rows())[0]?.codeCiphertext).toBeNull();
  });
});
