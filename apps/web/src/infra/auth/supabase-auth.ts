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
  private readonly client: ReturnType<typeof createClient>;

  constructor(url: string, secretKey: string) {
    this.client = createClient(url, secretKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
  }

  async sendOtp(phone: string): Promise<void> {
    const { error } = await this.client.auth.signInWithOtp({ phone });
    if (error) throw error;
  }

  async verifyOtp(phone: string, code: string): Promise<Session | null> {
    const { data, error } = await this.client.auth.verifyOtp({ phone, token: code, type: "sms" });
    if (error) {
      if (error.status !== undefined && error.status < 500) return null;
      throw error;
    }
    return toSession(data.session);
  }

  async refresh(refreshToken: string): Promise<Session | null> {
    const { data, error } = await this.client.auth.refreshSession({ refresh_token: refreshToken });
    if (error) {
      if (error.status !== undefined && error.status < 500) return null;
      throw error;
    }
    return toSession(data.session);
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
