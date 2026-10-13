// External service adapters against HTTP mocks (msw); no real credentials (decided in NUD-8).
import { generateKeyPairSync } from "node:crypto";
import { initializeApp, cert } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { otpMessage, TwilioOtpSender } from "../../src/infra/otp/twilio";
import { FcmPushSender } from "../../src/infra/push/fcm";

const mock = setupServer();
beforeAll(() => {
  mock.listen({ onUnhandledRequest: "error" });
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
    });
    await sender.send("+905320000002", "482913");

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
    });
    await expect(sender.send("+90", "1")).rejects.toThrow(/twilio: 400/);
  });

  it("keeps the SMS short enough for the SMS Retriever API", () => {
    expect(new TextEncoder().encode(otpMessage("123456", "FA+9qCX9VSu")).length).toBeLessThan(140);
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
    await push.send({ to: "selin", hint: "sync", taskId: null, event: "invite_accepted" });

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
