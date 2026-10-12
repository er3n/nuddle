// The API as a phone would call it, typed from the committed contract (ADR 0008), plus the E2E test
// endpoints (docs/testing.md, Backend E2E mode), which are not part of the contract.
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
}
