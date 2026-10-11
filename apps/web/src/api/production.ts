// Production wiring from environment variables (listed in .env.example). Missing provider
// credentials fall back to adapters that log (push) or fail (OTP), never to fakes.
import { readFile } from "node:fs/promises";
import { z } from "zod";
import type { Extraction } from "../domain/extraction";
import type { TaskExtractor } from "../domain/ports";
import { JoseTokenVerifier } from "../infra/auth/jwt";
import { SupabaseAuthGateway } from "../infra/auth/supabase-auth";
import { OffsetClock, systemClock } from "../infra/clock";
import { connectDb } from "../infra/db/client";
import { fcmTokensOf } from "../infra/db/store";
import { FixtureExtractor } from "../infra/llm/fixture-extractor";
import { OpenRouterExtractor } from "../infra/llm/openrouter-extractor";
import { TwilioOtpSender, unconfiguredOtpSender } from "../infra/otp/twilio";
import { FcmPushSender } from "../infra/push/fcm";
import { logPushSender } from "../infra/push/log";
import { SupabasePhotoStorage } from "../infra/storage/supabase-photos";
import { createApp, type App } from "./app";
import type { AppDeps } from "./deps";

const optional = z
  .string()
  .optional()
  .transform((v) => (v === undefined || v === "" ? null : v));

const Env = z.object({
  DATABASE_URL: z.string().min(1),
  SUPABASE_URL: z.url(),
  SUPABASE_SECRET_KEY: z.string().min(1),
  TICK_SECRET: z.string().min(32),
  SEND_SMS_HOOK_SECRET: z.string().startsWith("v1,whsec_"),
  TWILIO_ACCOUNT_SID: optional,
  TWILIO_AUTH_TOKEN: optional,
  TWILIO_FROM: optional,
  SMS_RETRIEVER_HASH: optional,
  FIREBASE_SERVICE_ACCOUNT: optional,
  OPENROUTER_API_KEY: optional,
  OPENROUTER_MODEL: optional,
  E2E: optional,
  E2E_EXTRACTOR_FIXTURES: z.string().default("../../e2e/fixtures/extractions.json"),
});

async function e2eExtractor(path: string): Promise<TaskExtractor> {
  const fixtures = JSON.parse(await readFile(path, "utf8")) as Record<string, Extraction>;
  return new FixtureExtractor(new Map(Object.entries(fixtures)));
}

export async function createProductionApp(
  source: Record<string, string | undefined> = process.env,
): Promise<App> {
  const env = Env.parse(source);
  const e2eMode = env.E2E === "1";
  const { db } = connectDb(env.DATABASE_URL);

  const e2e = e2eMode
    ? {
        clock: new OffsetClock(systemClock),
        adjustOutcome: (await import("./e2e")).compressOutcome,
      }
    : null;

  let extractor: TaskExtractor;
  if (e2eMode) {
    extractor = await e2eExtractor(env.E2E_EXTRACTOR_FIXTURES);
  } else if (env.OPENROUTER_API_KEY !== null && env.OPENROUTER_MODEL !== null) {
    extractor = new OpenRouterExtractor({
      apiKey: env.OPENROUTER_API_KEY,
      model: env.OPENROUTER_MODEL,
    });
  } else {
    throw new Error("OPENROUTER_API_KEY and OPENROUTER_MODEL are required outside E2E mode");
  }

  const otp =
    env.TWILIO_ACCOUNT_SID !== null && env.TWILIO_AUTH_TOKEN !== null && env.TWILIO_FROM !== null
      ? new TwilioOtpSender({
          accountSid: env.TWILIO_ACCOUNT_SID,
          authToken: env.TWILIO_AUTH_TOKEN,
          from: env.TWILIO_FROM,
          smsRetrieverHash: env.SMS_RETRIEVER_HASH,
        })
      : unconfiguredOtpSender;

  const push =
    env.FIREBASE_SERVICE_ACCOUNT === null
      ? logPushSender
      : FcmPushSender.fromServiceAccount(env.FIREBASE_SERVICE_ACCOUNT, (id) => fcmTokensOf(db, id));

  const deps: AppDeps = {
    db,
    clock: e2e?.clock ?? systemClock,
    push,
    otp,
    extractor,
    photos: new SupabasePhotoStorage(env.SUPABASE_URL, env.SUPABASE_SECRET_KEY),
    tokens: JoseTokenVerifier.forSupabase(env.SUPABASE_URL),
    auth: new SupabaseAuthGateway(env.SUPABASE_URL, env.SUPABASE_SECRET_KEY),
    tickSecret: env.TICK_SECRET,
    smsHookSecret: env.SEND_SMS_HOOK_SECRET,
    e2e,
  };
  return createApp(deps);
}
