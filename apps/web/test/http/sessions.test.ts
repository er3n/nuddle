// HTTP layer: which phone holds each session, Cihazlarım and signing out the others (ADR 0027).
import { eq } from "drizzle-orm";
import { v7 as uuidv7 } from "uuid";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { foreignDeviceUses, sessionDevices } from "../../src/infra/db/schema";
import { allPermissions } from "../builders";
import type { FakeAuthGateway, FakeClock, RecordingPushSender } from "../fakes";
import { startTestServer, type ApiClient, type TestServer } from "../support/server";
import { signUp } from "./helpers";

const PHONE = "+905320000011";

interface Phone {
  api: ApiClient;
  token: string;
  refreshToken: string;
  personId: string;
}

describe("http: sessions (ADR 0027)", () => {
  let server: TestServer;
  let auth: FakeAuthGateway;

  beforeEach(async () => {
    server = await startTestServer();
    auth = server.deps.auth as FakeAuthGateway;
  });
  afterEach(async () => {
    await server.close();
  });

  async function signIn(appVersion = "0.1.7", deviceId?: string): Promise<Phone> {
    const r = await server.client().POST("/api/v1/auth/verify", {
      body: { phone: PHONE, code: "123456", appVersion, ...(deviceId ? { deviceId } : {}) },
    });
    if (!r.data) throw new Error(`verify failed: ${JSON.stringify(r.error)}`);
    return {
      api: server.client(r.data.accessToken),
      token: r.data.accessToken,
      refreshToken: r.data.refreshToken,
      personId: r.data.personId,
    };
  }

  async function registerDevice(phone: Phone, fid: string, model: string): Promise<string> {
    const r = await phone.api.POST("/api/v1/devices", {
      body: {
        deviceId: null,
        fid,
        platform: "android",
        appVersion: "0.1.7",
        model,
        permissions: allPermissions,
      },
    });
    if (!r.data) throw new Error(`device failed: ${JSON.stringify(r.error)}`);
    return r.data.deviceId;
  }

  /** Two phones signed in to one account, each with its device registered. */
  async function twoPhones() {
    const pixel = await signIn();
    const register = await pixel.api.POST("/api/v1/commands/register", {
      body: { idempotencyKey: uuidv7(), displayName: "Sibel", timeZone: "Europe/Istanbul" },
    });
    expect(register.response.status).toBe(200);
    const pixelDevice = await registerDevice(pixel, "fid-pixel", "Google Pixel 8");
    const samsung = await signIn("0.1.6");
    const samsungDevice = await registerDevice(samsung, "fid-samsung", "Samsung SM-S911B");
    return { pixel, pixelDevice, samsung, samsungDevice };
  }

  const rowsOf = (personId: string) =>
    server.database.db.select().from(sessionDevices).where(eq(sessionDevices.personId, personId));

  it("keeps each session with its device, app version and last seen, never a token", async () => {
    const { pixel, pixelDevice, samsung, samsungDevice } = await twoPhones();
    const rows = await rowsOf(pixel.personId);
    expect(rows).toHaveLength(2);
    expect(rows.map((r) => r.deviceId).sort()).toEqual([pixelDevice, samsungDevice].sort());
    expect(rows.every((r) => r.lastSeenVia === "device")).toBe(true);
    expect(JSON.stringify(rows)).not.toContain(pixel.token);
    expect(JSON.stringify(rows)).not.toContain(samsung.refreshToken);

    const list = await pixel.api.GET("/api/v1/sessions");
    expect(list.data?.sessions).toHaveLength(2);
    expect(list.data?.sessions.find((s) => s.current)).toMatchObject({
      deviceModel: "Google Pixel 8",
      appVersion: "0.1.7",
    });
    expect(list.data?.sessions.find((s) => !s.current)).toMatchObject({
      deviceModel: "Samsung SM-S911B",
    });
  });

  it("a refresh keeps the session and moves its last seen", async () => {
    const { pixel, samsung, samsungDevice } = await twoPhones();
    const clock = server.deps.clock as FakeClock;
    clock.advance(60_000);
    const refreshed = await server.client().POST("/api/v1/auth/refresh", {
      body: { refreshToken: samsung.refreshToken, appVersion: "0.1.7", deviceId: samsungDevice },
    });
    expect(refreshed.response.status).toBe(200);
    const rows = await rowsOf(pixel.personId);
    expect(rows).toHaveLength(2);
    const row = rows.find((r) => r.deviceId === samsungDevice);
    expect(row).toMatchObject({ lastSeenVia: "refresh", appVersion: "0.1.7" });
  });

  it("a sync moves the session's last seen; a sync naming someone else's device is recorded and touches nothing", async () => {
    const { pixel, pixelDevice } = await twoPhones();
    const eren = await signUp(server, "Eren");
    const sync = await eren.api.GET("/api/v1/sync", {
      params: { query: { deviceId: pixelDevice } },
    });
    expect(sync.response.status).toBe(200);
    const uses = await server.database.db
      .select()
      .from(foreignDeviceUses)
      .where(eq(foreignDeviceUses.personId, eren.id));
    expect(uses.map((u) => [u.via, u.deviceId, u.ownerId, u.sessionId !== null])).toEqual([
      ["sync", pixelDevice, pixel.personId, true],
    ]);
    // Sibel's device was not touched and is not tied to Eren's session.
    const erenRows = await rowsOf(eren.id);
    expect(erenRows.some((r) => r.deviceId === pixelDevice)).toBe(false);

    const own = await pixel.api.GET("/api/v1/sync", {
      params: { query: { deviceId: pixelDevice } },
    });
    expect(own.response.status).toBe(200);
    const mine = (await rowsOf(pixel.personId)).find((r) => r.deviceId === pixelDevice);
    expect(mine?.lastSeenVia).toBe("sync");
  });

  it("records a log out and a sign-in naming a device that is not the caller's", async () => {
    const { pixel, pixelDevice } = await twoPhones();
    const eren = await signUp(server, "Eren");
    const out = await eren.api.POST("/api/v1/commands/log_out", {
      body: { idempotencyKey: uuidv7(), deviceId: pixelDevice },
    });
    expect(out.response.status).toBe(200);
    const logOuts = await server.database.db
      .select()
      .from(foreignDeviceUses)
      .where(eq(foreignDeviceUses.personId, eren.id));
    expect(logOuts.map((u) => [u.via, u.deviceId, u.ownerId])).toEqual([
      ["log_out", pixelDevice, pixel.personId],
    ]);

    const unknown = "00000000-0000-4000-8000-0000000000aa";
    const other = await server.client().POST("/api/v1/auth/verify", {
      body: { phone: "+905320000012", code: "123456", deviceId: unknown },
    });
    expect(other.response.status).toBe(200);
    const signIns = await server.database.db
      .select()
      .from(foreignDeviceUses)
      .where(eq(foreignDeviceUses.deviceId, unknown));
    expect(signIns.map((u) => [u.via, u.personId, u.ownerId])).toEqual([
      ["verify", other.data?.personId, null],
    ]);
  });

  it("signs out the other phones: Supabase ends them, their tokens stop at once, and a replay does not ask again", async () => {
    const { pixel, samsung, samsungDevice } = await twoPhones();
    const key = uuidv7();
    const r = await pixel.api.POST("/api/v1/commands/sign_out_others", {
      body: { idempotencyKey: key },
    });
    expect(r.data).toEqual({ signedOut: 1 });
    expect(auth.signedOutOthersOf).toEqual([pixel.token]);

    // The other phone's access token is refused now, and its refresh token no longer works.
    const stale = await samsung.api.GET("/api/v1/sync", {
      params: { query: { deviceId: samsungDevice } },
    });
    expect(stale.response.status).toBe(401);
    const refresh = await server.client().POST("/api/v1/auth/refresh", {
      body: { refreshToken: samsung.refreshToken },
    });
    expect(refresh.response.status).toBe(401);

    // This phone keeps working and lists only itself.
    const list = await pixel.api.GET("/api/v1/sessions");
    expect(list.data?.sessions.map((s) => s.current)).toEqual([true]);

    // The phones get a sync hint so the others hear the 401 now.
    const push = server.deps.push as RecordingPushSender;
    expect(push.sent.filter((m) => m.event === "signed_out_others").map((m) => m.to)).toEqual([
      pixel.personId,
    ]);

    const replay = await pixel.api.POST("/api/v1/commands/sign_out_others", {
      body: { idempotencyKey: key },
    });
    expect(replay.data).toEqual({ signedOut: 1 });
    expect(auth.signedOutOthersOf).toHaveLength(1);
  });

  it("a Supabase failure signs nobody out and stores nothing, so the same key can try again", async () => {
    const { pixel, samsung, samsungDevice } = await twoPhones();
    auth.failNextSignOut = true;
    const key = uuidv7();
    const failed = await pixel.api.POST("/api/v1/commands/sign_out_others", {
      body: { idempotencyKey: key },
    });
    expect(failed.response.status).toBe(502);
    const still = await samsung.api.GET("/api/v1/sync", {
      params: { query: { deviceId: samsungDevice } },
    });
    expect(still.response.status).toBe(200);

    const again = await pixel.api.POST("/api/v1/commands/sign_out_others", {
      body: { idempotencyKey: key },
    });
    expect(again.data).toEqual({ signedOut: 1 });
  });

  it("account deletion removes the session and foreign device rows", async () => {
    const { pixel } = await twoPhones();
    await pixel.api.GET("/api/v1/sync", {
      params: { query: { deviceId: "00000000-0000-4000-8000-0000000000bb" } },
    });
    const del = await pixel.api.POST("/api/v1/commands/delete_account", {
      body: { idempotencyKey: uuidv7() },
    });
    expect(del.response.status).toBe(200);
    expect(await rowsOf(pixel.personId)).toEqual([]);
    const uses = await server.database.db
      .select()
      .from(foreignDeviceUses)
      .where(eq(foreignDeviceUses.personId, pixel.personId));
    expect(uses).toEqual([]);
  });

  it("lists only sessions seen in the last 30 days", async () => {
    const { pixel } = await twoPhones();
    const clock = server.deps.clock as FakeClock;
    clock.advance(31 * 24 * 60 * 60_000);
    await pixel.api.GET("/api/v1/sync", { params: { query: {} } });
    const list = await pixel.api.GET("/api/v1/sessions");
    expect(list.data?.sessions).toHaveLength(1);
    expect(list.data?.sessions[0]?.current).toBe(true);
  });
});
