// Phone OTP sign-in through Supabase Auth, proxied by our API so the app talks only to us
// (ADR 0002, 0011; decided in NUD-8). Supabase sends the code through the Send SMS hook, which
// calls /api/internal/otp-hook.
import { createClient } from "@supabase/supabase-js";

export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresAt: Date;
  userId: string;
}

export interface AuthGateway {
  sendOtp(phone: string): Promise<void>;
  /** Null for a wrong or expired code. */
  verifyOtp(phone: string, code: string): Promise<Session | null>;
  /** Null for an unknown, revoked or reused refresh token. */
  refresh(refreshToken: string): Promise<Session | null>;
  /** E2E seeding only: the auth user id for a phone, created if missing. */
  ensureUser(phone: string): Promise<string>;
  /** Account deletion: the auth user and its phone go; an unknown id is already gone (NUD-89). */
  deleteUser(userId: string): Promise<void>;
  /** Ends every session of the token's user except the token's own (ADR 0027). */
  signOutOthers(accessToken: string): Promise<void>;
}

type SessionLike = {
  access_token: string;
  refresh_token: string;
  expires_at?: number;
  user: { id: string };
} | null;

function toSession(s: SessionLike): Session | null {
  if (!s) return null;
  return {
    accessToken: s.access_token,
    refreshToken: s.refresh_token,
    expiresAt: new Date((s.expires_at ?? 0) * 1000),
    userId: s.user.id,
  };
}

/** Supabase stores phones without the leading "+". */
const bare = (phone: string): string => phone.replace(/^\+/, "");

export class SupabaseAuthGateway implements AuthGateway {
  /** For the admin calls only: they carry no user session. */
  private readonly client: ReturnType<typeof createClient>;

  constructor(
    private readonly url: string,
    private readonly secretKey: string,
  ) {
    this.client = this.newClient();
  }

  /**
   * A supabase-js client holds one user's session and single-flights its refreshes: a second
   * `refreshSession` that starts while one is in flight gets the first one's result, whatever token
   * it sent. One server instance serves many people at once, so every call that signs someone in
   * or refreshes gets its own client (NUD-139).
   */
  private newClient(): ReturnType<typeof createClient> {
    return createClient(this.url, this.secretKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
  }

  async sendOtp(phone: string): Promise<void> {
    const { error } = await this.newClient().auth.signInWithOtp({ phone });
    if (error) throw error;
  }

  async verifyOtp(phone: string, code: string): Promise<Session | null> {
    const { data, error } = await this.newClient().auth.verifyOtp({
      phone,
      token: code,
      type: "sms",
    });
    if (error) {
      if (error.status !== undefined && error.status < 500) return null;
      throw error;
    }
    return toSession(data.session);
  }

  async refresh(refreshToken: string): Promise<Session | null> {
    const { data, error } = await this.newClient().auth.refreshSession({
      refresh_token: refreshToken,
    });
    if (error) {
      if (error.status !== undefined && error.status < 500) return null;
      throw error;
    }
    return toSession(data.session);
  }

  async deleteUser(userId: string): Promise<void> {
    const { error } = await this.client.auth.admin.deleteUser(userId);
    if (error && error.status !== 404) throw error;
  }

  async signOutOthers(accessToken: string): Promise<void> {
    const { error } = await this.client.auth.admin.signOut(accessToken, "others");
    if (error) throw error;
  }

  async ensureUser(phone: string): Promise<string> {
    const created = await this.client.auth.admin.createUser({ phone, phone_confirm: true });
    if (!created.error) return created.data.user.id;
    for (let page = 1; ; page++) {
      const { data, error } = await this.client.auth.admin.listUsers({ page, perPage: 1000 });
      if (error) throw error;
      const found = data.users.find((u) => u.phone === bare(phone));
      if (found) return found.id;
      if (data.users.length < 1000) throw created.error;
    }
  }
}
