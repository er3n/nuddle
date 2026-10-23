// The API under test (docs/testing.md, HTTP and Scenario layers): the real Hono app on a fresh
// copy of the migrated template database, with recording fakes for every external service and
// access tokens signed by a key generated per server. Requests go through `app.request()`.
import { randomBytes, randomUUID } from "node:crypto";
import { generateKeyPair, SignJWT } from "jose";
import createClient, { type Client } from "openapi-fetch";
import { Webhook } from "standardwebhooks";
import { inject } from "vitest";
import type { paths } from "../../../../packages/contract/openapi";
import { createApp, type App } from "../../src/api/app";
import type { AppDeps } from "../../src/api/deps";
import { JoseTokenVerifier } from "../../src/infra/auth/jwt";
import { AesGcmCodeCipher } from "../../src/infra/otp/code-cipher";
import { twilioSignature } from "../../src/infra/otp/twilio";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
import {
  FakeAuthGateway,
  FakeClock,
  FakeDelay,
  FakePhotoStorage,
  PendingWork,
  RecordingOtpSender,
  RecordingPushSender,
} from "../fakes";
import { createDatabaseFromTemplate, type TestDatabase } from "./db";

export type ApiClient = Client<paths>;

export const TICK_SECRET = "test-tick-secret";
/** Standard Webhooks secret: "v1,whsec_" + base64 key. */
export const SMS_HOOK_SECRET = `v1,whsec_${Buffer.from("test-send-sms-hook-secret").toString("base64")}`;

/** Twilio's auth token and the public status URL its callbacks are signed for (ADR 0029). */
export const TWILIO_AUTH_TOKEN = "test-twilio-auth-token";
export const TWILIO_STATUS_URL = "https://nuddle.test/api/internal/twilio-status";

/** A Send SMS hook request as Supabase Auth signs it (Standard Webhooks). */
export function signedSmsHook(
  phone: string,
  code: string,
  secret = SMS_HOOK_SECRET,
  at: Date = new Date(),
): RequestInit {
  const body = JSON.stringify({
    user: { id: "u1", phone: phone.replace(/^\+/, "") },
    sms: { otp: code },
  });
  const id = `msg_${randomUUID()}`;
  const signature = new Webhook(secret.replace(/^v1,whsec_/, "")).sign(id, at, body);
  return {
    method: "POST",
    body,
    headers: {
      "content-type": "application/json",
      "webhook-id": id,
      "webhook-timestamp": String(Math.floor(at.getTime() / 1000)),
      "webhook-signature": signature,
    },
  };
}

/** A Twilio status callback, signed like Twilio does unless a signature is given. */
export function twilioCallback(
  params: Record<string, string>,
  signature = twilioSignature(TWILIO_AUTH_TOKEN, TWILIO_STATUS_URL, params),
): RequestInit {
  return {
    method: "POST",
    body: new URLSearchParams(params).toString(),
    headers: {
      "content-type": "application/x-www-form-urlencoded",
      "x-twilio-signature": signature,
    },
  };
}

export interface TestServer {
  app: App;
  deps: AppDeps;
  database: TestDatabase;
  /** The 12 s fallback wait, ended by `delay.elapse()` (ADR 0029). */
  delay: FakeDelay;
  /** Work handed to `waitUntil`, awaited by `work.settle()`. */
  work: PendingWork;
  /** A Supabase-shaped access token (`sub`, `phone` without "+", audience authenticated). */
  tokenFor(userId: string, phone: string, sessionId?: string): Promise<string>;
  /** The generated TS client (ADR 0008) calling the app in-process, optionally signed in. */
  client(token?: string): ApiClient;
  close(): Promise<void>;
}

export async function startTestServer(overrides: Partial<AppDeps> = {}): Promise<TestServer> {
  const database = await createDatabaseFromTemplate(inject("templateDatabase"));
  const { privateKey, publicKey } = await generateKeyPair("ES256");
  // Every Supabase access token names its session (ADR 0027).
  const tokenFor = (userId: string, phone: string, sessionId: string = randomUUID()) =>
    new SignJWT({
      phone: phone.replace(/^\+/, ""),
      role: "authenticated",
      session_id: sessionId,
    })
      .setProtectedHeader({ alg: "ES256" })
      .setSubject(userId)
      .setAudience("authenticated")
      .setIssuedAt()
      .setExpirationTime("1h")
      .sign(privateKey);

  const clock = new FakeClock("2026-10-11T20:00:00+03:00");
  const delay = new FakeDelay();
  const work = new PendingWork();
  const deps: AppDeps = {
    db: database.db,
    clock,
    push: new RecordingPushSender(clock),
    otp: new RecordingOtpSender(),
    otpDelivery: {
      delay,
      waitUntil: work.waitUntil,
      cipher: new AesGcmCodeCipher(randomBytes(32).toString("base64")),
      twilioAuthToken: TWILIO_AUTH_TOKEN,
      statusUrl: TWILIO_STATUS_URL,
    },
    extractor: new FixtureExtractor(new Map()),
    planner: null,
    profiler: null,
    background: (job) => job(),
    photos: new FakePhotoStorage(),
    tokens: new JoseTokenVerifier(publicKey),
    auth: new FakeAuthGateway(tokenFor),
    tickSecret: TICK_SECRET,
    smsHookSecret: SMS_HOOK_SECRET,
    version: "test",
    e2e: null,
    ...overrides,
  };
  const app = await createApp(deps);
  return {
    app,
    deps,
    database,
    delay,
    work,
    tokenFor,
    client: (token) =>
      createClient<paths>({
        baseUrl: "http://api.test",
        fetch: (request: Request) => Promise.resolve(app.fetch(request)),
        ...(token === undefined ? {} : { headers: { Authorization: `Bearer ${token}` } }),
      }),
    close: database.drop,
  };
}
