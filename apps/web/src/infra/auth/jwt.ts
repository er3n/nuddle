// Verifies Supabase Auth access tokens (ADR 0011). Supabase signs with an asymmetric key published
// at `/auth/v1/.well-known/jwks.json`; tests verify against a key they generate.
import { createRemoteJWKSet, jwtVerify, type CryptoKey, type JWTVerifyGetKey } from "jose";

export interface Caller {
  /** The Supabase Auth user id, which is also the person id. */
  id: string;
  /** E.164 with a leading "+" (Supabase stores phones without it). */
  phone: string;
  /** The Supabase session the token belongs to (`session_id`, ADR 0027); null if the token has none. */
  sessionId: string | null;
}

export interface TokenVerifier {
  /** Throws when the token is missing a phone, expired, or not signed by Supabase Auth. */
  verify(token: string): Promise<Caller>;
}

export class JoseTokenVerifier implements TokenVerifier {
  constructor(private readonly key: JWTVerifyGetKey | CryptoKey) {}

  static forSupabase(supabaseUrl: string): JoseTokenVerifier {
    return new JoseTokenVerifier(
      createRemoteJWKSet(new URL("/auth/v1/.well-known/jwks.json", supabaseUrl)),
    );
  }

  async verify(token: string): Promise<Caller> {
    const { payload } =
      typeof this.key === "function"
        ? await jwtVerify(token, this.key, { audience: "authenticated" })
        : await jwtVerify(token, this.key, { audience: "authenticated" });
    const { sub, phone, session_id: sessionId } = payload;
    if (typeof sub !== "string" || typeof phone !== "string" || phone === "") {
      throw new Error("token has no subject or phone");
    }
    return {
      id: sub,
      phone: phone.startsWith("+") ? phone : `+${phone}`,
      sessionId: typeof sessionId === "string" ? sessionId : null,
    };
  }
}
