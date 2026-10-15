// The API under test (docs/testing.md, HTTP and Scenario layers): the real Hono app on a fresh
// copy of the migrated template database, with recording fakes for every external service and
// access tokens signed by a key generated per server. Requests go through `app.request()`.
import { generateKeyPair, SignJWT } from "jose";
import createClient, { type Client } from "openapi-fetch";
import { inject } from "vitest";
import type { paths } from "../../../../packages/contract/openapi";
import { createApp, type App } from "../../src/api/app";
import type { AppDeps } from "../../src/api/deps";
import { JoseTokenVerifier } from "../../src/infra/auth/jwt";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
import {
  FakeAuthGateway,
  FakeClock,
  FakePhotoStorage,
  RecordingOtpSender,
  RecordingPushSender,
} from "../fakes";
import { createDatabaseFromTemplate, type TestDatabase } from "./db";

export type ApiClient = Client<paths>;

export const TICK_SECRET = "test-tick-secret";
/** Standard Webhooks secret: "v1,whsec_" + base64 key. */
export const SMS_HOOK_SECRET = `v1,whsec_${Buffer.from("test-send-sms-hook-secret").toString("base64")}`;

export interface TestServer {
  app: App;
  deps: AppDeps;
  database: TestDatabase;
  /** A Supabase-shaped access token (`sub`, `phone` without "+", audience authenticated). */
  tokenFor(userId: string, phone: string): Promise<string>;
  /** The generated TS client (ADR 0008) calling the app in-process, optionally signed in. */
  client(token?: string): ApiClient;
  close(): Promise<void>;
}

export async function startTestServer(overrides: Partial<AppDeps> = {}): Promise<TestServer> {
  const database = await createDatabaseFromTemplate(inject("templateDatabase"));
  const { privateKey, publicKey } = await generateKeyPair("ES256");
  const tokenFor = (userId: string, phone: string) =>
    new SignJWT({ phone: phone.replace(/^\+/, ""), role: "authenticated" })
      .setProtectedHeader({ alg: "ES256" })
      .setSubject(userId)
      .setAudience("authenticated")
      .setIssuedAt()
      .setExpirationTime("1h")
      .sign(privateKey);

  const clock = new FakeClock("2026-10-11T20:00:00+03:00");
  const deps: AppDeps = {
    db: database.db,
    clock,
    push: new RecordingPushSender(clock),
    otp: new RecordingOtpSender(),
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
