// The real adapters against the local Supabase stack: phone sign-in through our API with the
// Send SMS hook's test numbers, JWT verification through JWKS, Storage, and E2E seeding.
import { v7 as uuidv7 } from "uuid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createApp } from "../../src/api/app";
import { createProductionApp } from "../../src/api/production";
import { JoseTokenVerifier } from "../../src/infra/auth/jwt";
import { SupabaseAuthGateway } from "../../src/infra/auth/supabase-auth";
import { SupabasePhotoStorage } from "../../src/infra/storage/supabase-photos";
import { FixtureExtractor } from "../../src/infra/llm/fixture-extractor";
import { FakeClock, RecordingOtpSender, RecordingPushSender } from "../fakes";
import { createTestDatabase, type TestDatabase } from "../support/db";
import { localSupabase } from "../support/local-supabase";

const json = (body: object): RequestInit => ({
  method: "POST",
  body: JSON.stringify(body),
  headers: { "content-type": "application/json" },
});

describe("local Supabase adapters", () => {
  let t: TestDatabase;
  const { url, secretKey } = localSupabase();

  beforeAll(async () => {
    t = await createTestDatabase();
  });
  afterAll(async () => {
    await t.drop();
  });

  it("signs in with a test number through /api/v1/auth and the JWKS-verified token works", async () => {
    const clock = new FakeClock("2026-10-11T20:00:00+03:00");
    const photos = new SupabasePhotoStorage(url, secretKey);
    const app = await createApp({
      db: t.db,
      clock,
      push: new RecordingPushSender(clock),
      otp: new RecordingOtpSender(),
      extractor: new FixtureExtractor(new Map()),
      planner: null,
      profiler: null,
      background: (job) => job(),
      photos,
      tokens: JoseTokenVerifier.forSupabase(url),
      auth: new SupabaseAuthGateway(url, secretKey),
      tickSecret: "unused-in-this-test",
      smsHookSecret: "v1,whsec_dW51c2Vk",
      version: "test",
      e2e: null,
    });

    // +905320000006 is a test number with code 123456 (supabase/config.toml).
    expect((await app.request("/api/v1/auth/otp", json({ phone: "+905320000006" }))).status).toBe(
      200,
    );
    const wrong = await app.request(
      "/api/v1/auth/verify",
      json({ phone: "+905320000006", code: "000000" }),
    );
    expect(wrong.status).toBe(401);
    const res = await app.request(
      "/api/v1/auth/verify",
      json({ phone: "+905320000006", code: "123456" }),
    );
    expect(res.status).toBe(200);
    const session = (await res.json()) as {
      accessToken: string;
      personId: string;
      registered: boolean;
    };
    expect(session.registered).toBe(false);

    const auth = {
      authorization: `Bearer ${session.accessToken}`,
      "content-type": "application/json",
    };
    const reg = await app.request("/api/v1/commands/register", {
      method: "POST",
      headers: auth,
      body: JSON.stringify({
        idempotencyKey: uuidv7(),
        displayName: "Test",
        timeZone: "Europe/Istanbul",
      }),
    });
    expect(reg.status).toBe(200);
    expect(await reg.json()).toMatchObject({ id: session.personId, phone: "+905320000006" });
    const sync = await app.request("/api/v1/sync", { headers: auth });
    expect(sync.status).toBe(200);
  });

  it("gives each of two refreshes at the same moment its own person's session (NUD-139)", async () => {
    // One gateway serves every request on a server instance, as in production.
    const auth = new SupabaseAuthGateway(url, secretKey);
    await auth.sendOtp("+905320000004");
    await auth.sendOtp("+905320000005");
    const first = await auth.verifyOtp("+905320000004", "123456");
    const second = await auth.verifyOtp("+905320000005", "123456");
    if (!first || !second) throw new Error("test numbers did not sign in");
    expect(first.userId).not.toBe(second.userId);

    const [a, b] = await Promise.all([
      auth.refresh(first.refreshToken),
      auth.refresh(second.refreshToken),
    ]);
    expect(a?.userId).toBe(first.userId);
    expect(b?.userId).toBe(second.userId);
  });

  it("uploads to a signed URL in the private bucket and deletes the task's photos", async () => {
    const photos = new SupabasePhotoStorage(url, secretKey);
    const taskId = uuidv7();
    const { path, url: uploadUrl } = await photos.createUploadUrl(taskId);
    expect(path).toMatch(new RegExp(`^tasks/${taskId}/.+\\.jpg$`));

    const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0xff, 0xd9]);
    const put = await fetch(uploadUrl, {
      method: "PUT",
      body: jpeg,
      headers: { "content-type": "image/jpeg" },
    });
    expect(put.status).toBe(200);

    const listed = await fetch(`${url}/storage/v1/object/list/task-photos`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${secretKey}`,
        apikey: secretKey,
        "content-type": "application/json",
      },
      body: JSON.stringify({ prefix: `tasks/${taskId}` }),
    });
    expect(((await listed.json()) as unknown[]).length).toBe(1);

    // 4f: the sender looks at it through a short-lived signed URL.
    const view = await fetch(await photos.createViewUrl(path));
    expect(view.status).toBe(200);
    expect(new Uint8Array(await view.arrayBuffer())).toEqual(jpeg);

    await photos.deleteForTask(taskId);
    const after = await fetch(`${url}/storage/v1/object/list/task-photos`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${secretKey}`,
        apikey: secretKey,
        "content-type": "application/json",
      },
      body: JSON.stringify({ prefix: `tasks/${taskId}` }),
    });
    expect(await after.json()).toEqual([]);
    // Deleting an empty folder is fine (a task without photos closes).
    await photos.deleteForTask(taskId);
  });

  it("E2E seed creates auth users, so test numbers sign in as the seeded people", async () => {
    const [row] = await t.sql<{ name: string }[]>`select current_database() as name`;
    const db = new URL("postgresql://postgres:postgres@127.0.0.1:54322/");
    db.pathname = `/${row?.name ?? ""}`;
    const app = await createProductionApp({
      DATABASE_URL: db.toString(),
      SUPABASE_URL: url,
      SUPABASE_SECRET_KEY: secretKey,
      TICK_SECRET: "x".repeat(32),
      SEND_SMS_HOOK_SECRET: "v1,whsec_dW51c2Vk",
      E2E: "1",
      E2E_EXTRACTOR_FIXTURES: new URL("../../../../e2e/fixtures/extractions.json", import.meta.url)
        .pathname,
    });
    expect((await app.request("/api/test/reset", json({}))).status).toBe(200);
    const seeded = await app.request(
      "/api/test/seed",
      json({
        people: [
          { phone: "+905320000003", displayName: "Defne" },
          { phone: "+905320000004", displayName: "Can" },
        ],
        connections: [
          { from: "+905320000003", to: "+905320000004", type: "sibling" },
          { from: "+905320000004", to: "+905320000003", type: "sibling" },
        ],
      }),
    );
    expect(seeded.status).toBe(200);
    const { people } = (await seeded.json()) as { people: Record<string, string> };

    const auth = new SupabaseAuthGateway(url, secretKey);
    await auth.sendOtp("+905320000003");
    const session = await auth.verifyOtp("+905320000003", "123456");
    expect(session?.userId).toBe(people["+905320000003"]);
  });
});
