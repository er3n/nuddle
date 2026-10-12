// The API as a phone would call it, typed from the committed contract (ADR 0008), plus the E2E test
// endpoints (docs/testing.md, Backend E2E mode), which are not part of the contract.
import { randomBytes } from "node:crypto";
import createClient, { type Client } from "openapi-fetch";
import type { components, paths } from "../../../packages/contract/openapi";
import { API_URL } from "./backend";

export type SyncTask = components["schemas"]["SyncTask"];
export type Session = components["schemas"]["Session"];
export type SyncConnection = components["schemas"]["SyncConnection"];

/** Test OTP code of the local stack (supabase/config.toml, auth.sms.test_otp). */
const TEST_OTP_CODE = "123456";

async function postTest(path: string, body: unknown = {}): Promise<unknown> {
  const res = await fetch(`${API_URL}/api/test/${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST /api/test/${path}: ${String(res.status)} ${await res.text()}`);
  return res.json();
}

export const testApi = {
  reset: () => postTest("reset"),
  tick: () => postTest("tick"),
  advanceClock: (ms: number) => postTest("advance-clock", { ms }),
};

function unwrap<T>(what: string, r: { data?: T; error?: unknown; response: Response }): T {
  if (r.data === undefined) {
    throw new Error(`${what}: ${String(r.response.status)} ${JSON.stringify(r.error)}`);
  }
  return r.data;
}

/** A UUIDv7, as the app makes idempotency keys (ADR 0006): 48 bits of milliseconds, version 7, random rest. */
function uuidv7(): string {
  const b = randomBytes(16);
  b.writeUIntBE(Date.now(), 0, 6);
  b.writeUInt8((b.readUInt8(6) & 0x0f) | 0x70, 6);
  b.writeUInt8((b.readUInt8(8) & 0x3f) | 0x80, 8);
  const h = b.toString("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export async function signIn(phone: string): Promise<Session> {
  const anon = createClient<paths>({ baseUrl: API_URL });
  unwrap("sendOtp", await anon.POST("/api/v1/auth/otp", { body: { phone } }));
  return unwrap(
    "verifyOtp",
    await anon.POST("/api/v1/auth/verify", { body: { phone, code: TEST_OTP_CODE } }),
  );
}

export class Phone {
  private readonly client: Client<paths>;

  constructor(readonly session: Session) {
    this.client = createClient<paths>({
      baseUrl: API_URL,
      headers: { authorization: `Bearer ${session.accessToken}` },
    });
  }

  async tasks(): Promise<SyncTask[]> {
    return unwrap("sync", await this.client.GET("/api/v1/sync", {})).tasks;
  }

  async connections(): Promise<SyncConnection[]> {
    return unwrap("sync", await this.client.GET("/api/v1/sync", {})).connections;
  }

  /** A task to self, as the preview's "Kur" sends it; returns the task id. */
  async createTaskToSelf(title: string, deadline: Date): Promise<string> {
    const r = await this.client.POST("/api/v1/commands/create_task", {
      body: {
        idempotencyKey: uuidv7(),
        recipientId: this.session.personId,
        title,
        detail: null,
        deadline: deadline.toISOString(),
        finishMode: "done",
        rawText: null,
      },
    });
    return unwrap("create_task", r).taskId;
  }
}
