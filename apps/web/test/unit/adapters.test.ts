// External service adapters against HTTP mocks (msw); no real credentials (decided in NUD-8).
import { generateKeyPairSync } from "node:crypto";
import { initializeApp, cert } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import {
  isTwilioSignatureValid,
  otpMessage,
  TwilioOtpSender,
  twilioSignature,
} from "../../src/infra/otp/twilio";
import { FcmPushSender } from "../../src/infra/push/fcm";

const mock = setupServer();
beforeAll(() => {
  mock.listen({ onUnhandledFrame: "error" });
});
afterEach(() => {
  mock.resetHandlers();
});
afterAll(() => {
  mock.close();
});

describe("TwilioOtpSender", () => {
  const url = "https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json";

  it("posts the code to Twilio's Messages API with basic auth", async () => {
    const seen: { auth: string | null; form: URLSearchParams }[] = [];
    mock.use(
      http.post(url, async ({ request }) => {
        seen.push({
          auth: request.headers.get("authorization"),
          form: new URLSearchParams(await request.text()),
        });
        return HttpResponse.json({ sid: "SM1" }, { status: 201 });
      }),
    );
    const sender = new TwilioOtpSender({
      accountSid: "AC123",
      authToken: "token",
      from: "MG456",
      smsRetrieverHash: "FA+9qCX9VSu",
      statusCallback: null,
      whatsApp: null,
    });
    await sender.sendSms("+905320000002", "482913");

    expect(seen).toHaveLength(1);
    expect(seen[0]?.auth).toBe(`Basic ${Buffer.from("AC123:token").toString("base64")}`);
    expect(Object.fromEntries(seen[0]?.form ?? [])).toEqual({
      To: "+905320000002",
      MessagingServiceSid: "MG456",
      Body: "dürt kodun: 482913\n\nFA+9qCX9VSu",
    });
  });

  it("uses From for a sender number and fails on a Twilio error", async () => {
    mock.use(
      http.post(url, async ({ request }) => {
        const form = new URLSearchParams(await request.text());
        expect(form.get("From")).toBe("+15005550006");
        return HttpResponse.json({ code: 21211, message: "invalid To" }, { status: 400 });
      }),
    );
    const sender = new TwilioOtpSender({
      accountSid: "AC123",
      authToken: "token",
      from: "+15005550006",
      smsRetrieverHash: null,
      statusCallback: null,
      whatsApp: null,
    });
    await expect(sender.sendSms("+90", "1")).rejects.toThrow(/twilio: 400/);
  });

  it("keeps the SMS short enough for the SMS Retriever API", () => {
    expect(new TextEncoder().encode(otpMessage("123456", "FA+9qCX9VSu")).length).toBeLessThan(140);
  });

  const statusCallback = "https://nuddle.app/api/internal/twilio-status";
  const withWhatsApp = {
    accountSid: "AC123",
    authToken: "token",
    from: "+15005550006",
    smsRetrieverHash: "FA+9qCX9VSu",
    statusCallback,
    whatsApp: { from: "+15005550006", templates: { tr: "HXtr", en: "HXen" } },
  };

  it("sends the WhatsApp template in the number's language with the code and the status callback (ADR 0029)", async () => {
    const forms: Record<string, string>[] = [];
    mock.use(
      http.post(url, async ({ request }) => {
        forms.push(Object.fromEntries(new URLSearchParams(await request.text())));
        return HttpResponse.json({ sid: `MM${String(forms.length)}` }, { status: 201 });
      }),
    );
    const sender = new TwilioOtpSender(withWhatsApp);
    expect(sender.whatsApp).toBe(true);
    expect(await sender.sendWhatsApp("+905320000002", "482913", "tr")).toEqual({ sid: "MM1" });
    expect(await sender.sendWhatsApp("+4915112345678", "111222", "en")).toEqual({ sid: "MM2" });

    expect(forms).toEqual([
      {
        To: "whatsapp:+905320000002",
        From: "whatsapp:+15005550006",
        ContentSid: "HXtr",
        ContentVariables: JSON.stringify({ "1": "482913" }),
        StatusCallback: statusCallback,
      },
      {
        To: "whatsapp:+4915112345678",
        From: "whatsapp:+15005550006",
        ContentSid: "HXen",
        ContentVariables: JSON.stringify({ "1": "111222" }),
        StatusCallback: statusCallback,
      },
    ]);
  });

  it("asks for the SMS status too, so its price is kept", async () => {
    const forms: Record<string, string>[] = [];
    mock.use(
      http.post(url, async ({ request }) => {
        forms.push(Object.fromEntries(new URLSearchParams(await request.text())));
        return HttpResponse.json({ sid: "SM9" }, { status: 201 });
      }),
    );
    expect(await new TwilioOtpSender(withWhatsApp).sendSms("+905320000002", "482913")).toEqual({
      sid: "SM9",
    });
    expect(forms).toEqual([
      {
        To: "+905320000002",
        From: "+15005550006",
        Body: "dürt kodun: 482913\n\nFA+9qCX9VSu",
        StatusCallback: statusCallback,
      },
    ]);
  });

  it("without the WhatsApp settings it sends SMS only, as before the sender was approved", async () => {
    const sender = new TwilioOtpSender({ ...withWhatsApp, whatsApp: null });
    expect(sender.whatsApp).toBe(false);
    await expect(sender.sendWhatsApp("+905320000002", "482913", "tr")).rejects.toThrow(
      /not configured/,
    );
  });

  it("reads a message's price, null while Twilio has not priced it", async () => {
    const prices: Record<string, string | null> = { SM1: "-0.03050", SM2: null };
    mock.use(
      http.get(
        "https://api.twilio.com/2010-04-01/Accounts/AC123/Messages/:sid.json",
        ({ params }) => HttpResponse.json({ price: prices[String(params.sid)] ?? null }),
      ),
    );
    const sender = new TwilioOtpSender(withWhatsApp);
    expect(await sender.priceOf("SM1")).toBe(0.0305);
    expect(await sender.priceOf("SM2")).toBeNull();
  });
});

describe("Twilio request signature", () => {
  // Computed with Twilio's own helper (twilio@5.10.0 getExpectedTwilioSignature).
  const authToken = "test-token";
  const statusUrl = "https://nuddle.app/api/internal/twilio-status";
  const params = {
    MessageSid: "MM00000000000000000000000000000001",
    MessageStatus: "delivered",
    AccountSid: "AC123",
    ErrorCode: "",
    ChannelPrefix: "whatsapp",
  };
  const expected = "kcQstKlTJi9BlMC64g8VqkDT0tU=";

  it("matches Twilio's HMAC-SHA1 over the URL and the sorted parameters", () => {
    expect(twilioSignature(authToken, statusUrl, params)).toBe(expected);
  });

  it("accepts the right signature", () => {
    expect(isTwilioSignatureValid(authToken, statusUrl, params, expected)).toBe(true);
  });

  it("refuses a wrong token, another URL, a changed parameter or a missing signature", () => {
    expect(isTwilioSignatureValid("other-token", statusUrl, params, expected)).toBe(false);
    expect(
      isTwilioSignatureValid(
        authToken,
        "https://evil.test/api/internal/twilio-status",
        params,
        expected,
      ),
    ).toBe(false);
    expect(
      isTwilioSignatureValid(
        authToken,
        statusUrl,
        { ...params, MessageStatus: "failed" },
        expected,
      ),
    ).toBe(false);
    expect(isTwilioSignatureValid(authToken, statusUrl, { ...params, Extra: "1" }, expected)).toBe(
      false,
    );
    expect(isTwilioSignatureValid(authToken, statusUrl, params, "")).toBe(false);
  });
});

describe("FcmPushSender", () => {
  it("sends a high priority data message to the FID of every device of the person", async () => {
    const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
    const app = initializeApp(
      {
        credential: cert({
          projectId: "nuddle-test",
          clientEmail: "push@nuddle-test.iam.gserviceaccount.com",
          privateKey: privateKey.export({ type: "pkcs8", format: "pem" }).toString(),
        }),
      },
      "fcm-test",
    );
    const messaging = getMessaging(app);
    // Production sends over HTTP/2, which msw cannot intercept; the request body is the same.
    // eslint-disable-next-line @typescript-eslint/no-deprecated -- msw intercepts HTTP/1.1 only; the request body under test is the same over HTTP/2.
    messaging.enableLegacyHttpTransport();

    const sent: unknown[] = [];
    mock.use(
      http.post("https://oauth2.googleapis.com/token", () =>
        HttpResponse.json({ access_token: "test-token", expires_in: 3600, token_type: "Bearer" }),
      ),
      http.post(
        "https://fcm.googleapis.com/v1/projects/nuddle-test/messages:send",
        async ({ request }) => {
          expect(request.headers.get("authorization")).toBe("Bearer test-token");
          sent.push(await request.json());
          return HttpResponse.json({ name: "projects/nuddle-test/messages/1" });
        },
      ),
    );

    const push = new FcmPushSender(messaging, (id) =>
      Promise.resolve(id === "eren" ? ["fid-a", "fid-b"] : []),
    );
    await push.send({ to: "eren", hint: "plan_changed", taskId: "task-1", event: "created" });
    await push.send({ to: "sibel", hint: "sync", taskId: null, event: "invite_accepted" });

    expect(sent).toEqual(
      ["fid-a", "fid-b"].map((fid) => ({
        message: {
          fid,
          data: { hint: "plan_changed", taskId: "task-1", event: "created" },
          android: { priority: "high" },
        },
      })),
    );
  });
});
