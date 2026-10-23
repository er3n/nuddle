// Sign-in over WhatsApp with the SMS fallback, end to end (ADR 0029). Eren's app asks for a code
// through the generated client; Supabase Auth (faked) calls the signed Send SMS hook; Twilio
// (faked) reports statuses through the signed callback; time moves only through the injected
// clock, the fake 12 s delay and the tick. Sign-in is one person's flow, so no second phone.
import { eq } from "drizzle-orm";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DAY } from "../../src/domain/time";
import { otpSends } from "../../src/infra/db/schema";
import { aPerson } from "../builders";
import { type FakeAuthGateway, type FakeClock, RecordingOtpSender } from "../fakes";
import {
  signedSmsHook,
  startTestServer,
  TICK_SECRET,
  twilioCallback,
  type TestServer,
} from "../support/server";

const live: TestServer[] = [];
afterEach(async () => {
  for (const s of live.splice(0)) await s.close();
});

/** The code Supabase Auth issues in this world; the fake gateway accepts only it. */
const CODE = "123456";

async function signInWorld() {
  const otp = new RecordingOtpSender({ whatsApp: true });
  const server = await startTestServer({ otp });
  live.push(server);
  const clock = server.deps.clock as FakeClock;
  const auth = server.deps.auth as FakeAuthGateway;
  auth.onSendOtp = async (phone) => {
    const res = await server.app.request("/api/internal/otp-hook", signedSmsHook(phone, CODE));
    if (res.status !== 200) throw new Error(`hook answered ${String(res.status)}`);
  };
  const eren = aPerson("Eren").phone;
  const api = server.client();

  return {
    otp,
    clock,
    eren,
    /** Eren taps "Kod gönder" on 0c. */
    async askCode() {
      const r = await api.POST("/api/v1/auth/otp", { body: { phone: eren } });
      if (!r.data) throw new Error(`auth/otp failed: ${JSON.stringify(r.error)}`);
      return r.data.channel;
    },
    /** Eren taps "SMS ile gönder" on 0d. */
    async askSms() {
      const r = await api.POST("/api/v1/auth/otp/sms", { body: { phone: eren } });
      if (!r.data) throw new Error(`auth/otp/sms failed: ${JSON.stringify(r.error)}`);
      return r.data.channel;
    },
    /** Eren types or autofills the code on 0d. */
    async verify(code: string) {
      const r = await api.POST("/api/v1/auth/verify", { body: { phone: eren, code } });
      return r.response.status;
    },
    /** Twilio's status callback for a message. */
    async twilio(sid: string | undefined, status: string, errorCode?: string) {
      const res = await server.app.request(
        "/api/internal/twilio-status",
        twilioCallback({
          MessageSid: sid ?? "",
          MessageStatus: status,
          ...(errorCode === undefined ? {} : { ErrorCode: errorCode }),
        }),
      );
      expect(res.status).toBe(204);
    },
    /** 12 s pass and the hook's timer ends, if the function is still alive. */
    async twelveSecondsPass(timerRuns = true) {
      clock.advance(12_000);
      if (timerRuns) {
        server.delay.elapse();
        await server.work.settle();
      }
    },
    async tick() {
      const res = await server.app.request("/api/internal/tick", {
        method: "POST",
        headers: { Authorization: `Bearer ${TICK_SECRET}` },
      });
      expect(res.status).toBe(200);
    },
    async rows() {
      return server.database.db.select().from(otpSends).where(eq(otpSends.phone, eren));
    },
    /** The timer of every earlier code ends too (nothing may be sent by it any more). */
    async settle() {
      server.delay.elapse();
      await server.work.settle();
    },
  };
}

describe("scenarios: sign-in codes over WhatsApp", () => {
  it("WhatsApp delivers: no SMS, and the correct code signs in and clears the held code", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    expect(w.otp.whatsAppSent).toMatchObject([{ phone: w.eren, code: CODE, language: "tr" }]);
    const sid = w.otp.whatsAppSent[0]?.sid;

    await w.twilio(sid, "sent");
    await w.twilio(sid, "delivered");
    await w.twelveSecondsPass();
    await w.tick();
    expect(w.otp.sent).toEqual([]);

    expect(await w.verify(CODE)).toBe(200);
    expect(await w.rows()).toMatchObject([
      { channel: "whatsapp", status: "delivered", fallbackAt: null, codeCiphertext: null },
    ]);
  });

  it("WhatsApp fails (no WhatsApp on the number): the same code goes by SMS at once, and next time straight by SMS", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    await w.twilio(w.otp.whatsAppSent[0]?.sid, "failed", "63003");
    expect(w.otp.sent).toEqual([{ phone: w.eren, code: CODE }]);

    // The timer and the tick find the SMS already sent.
    await w.twelveSecondsPass();
    await w.tick();
    expect(w.otp.sent).toHaveLength(1);
    expect(await w.verify(CODE)).toBe(200);

    // A day later the number is remembered: SMS only, no WhatsApp, no wait.
    w.clock.advance(DAY);
    expect(await w.askCode()).toBe("sms");
    expect(w.otp.whatsAppSent).toHaveLength(1);
    expect(w.otp.sent).toHaveLength(2);

    // After 30 days WhatsApp is tried again.
    w.clock.advance(30 * DAY);
    expect(await w.askCode()).toBe("whatsapp");
    expect(w.otp.whatsAppSent).toHaveLength(2);
    // One row per code; the failed one is over 31 days old now and was deleted with this send.
    expect((await w.rows()).map((r) => r.channel)).toEqual(["sms", "whatsapp"]);
  });

  it("WhatsApp says nothing for 12 s: the SMS goes, and a late delivered sends nothing more", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    const sid = w.otp.whatsAppSent[0]?.sid;
    await w.twilio(sid, "sent");
    await w.twelveSecondsPass();
    expect(w.otp.sent).toEqual([{ phone: w.eren, code: CODE }]);

    await w.twilio(sid, "delivered");
    await w.tick();
    expect(w.otp.sent).toHaveLength(1);
    const [row] = await w.rows();
    expect(row).toMatchObject({ status: "delivered", codeCiphertext: null });
    expect(row?.fallbackAt).toEqual(w.clock.now());

    // Delivered late is not a failure: the next code tries WhatsApp again.
    expect(await w.verify(CODE)).toBe(200);
    expect(await w.askCode()).toBe("whatsapp");
  });

  it("the function stopped before its timer: the tick sends the SMS once", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    await w.twelveSecondsPass(false);
    await w.tick();
    expect(w.otp.sent).toEqual([{ phone: w.eren, code: CODE }]);

    // The timer, if it ever ends, and another tick find it done.
    await w.settle();
    await w.tick();
    expect(w.otp.sent).toHaveLength(1);
  });

  it("the timer, the failed callback and the tick racing still send one SMS", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    w.clock.advance(12_000);
    await Promise.all([
      w.settle(),
      w.twilio(w.otp.whatsAppSent[0]?.sid, "undelivered", "63016"),
      w.tick(),
    ]);
    expect(w.otp.sent).toEqual([{ phone: w.eren, code: CODE }]);
  });

  it("while one SMS is still in flight, no other path sends a second", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    const release = w.otp.holdNextSms();
    const failed = w.twilio(w.otp.whatsAppSent[0]?.sid, "failed", "63003");
    // The callback claimed the row and its SMS is at Twilio.
    await vi.waitFor(() => {
      expect(w.otp.inFlight).toBe(1);
    });
    w.clock.advance(12_000);
    await w.tick();
    await w.settle();
    await expect(w.askSms()).rejects.toThrow(/otp_resend_unavailable/);
    release();
    await failed;
    expect(w.otp.sent).toEqual([{ phone: w.eren, code: CODE }]);
  });

  it("'SMS ile gönder' before the fallback: one SMS, and the timer sends no second one", async () => {
    const w = await signInWorld();
    expect(await w.askCode()).toBe("whatsapp");
    // 0d offers the button 5 s after the code was sent (open-questions #66).
    w.clock.advance(5_000);
    expect(await w.askSms()).toBe("sms");
    expect(w.otp.sent).toEqual([{ phone: w.eren, code: CODE }]);

    await w.twelveSecondsPass();
    await w.tick();
    expect(w.otp.sent).toHaveLength(1);
    expect(await w.verify(CODE)).toBe(200);
  });
});
