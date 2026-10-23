// The sign-in code held for the SMS fallback (ADR 0029): AES-256-GCM with OTP_CODE_KEY (base64 of
// 32 bytes, Vercel env only). The stored text is base64 of IV (12 bytes), tag (16 bytes) and the
// encrypted code. Neither the code nor the ciphertext is ever logged.
import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import type { CodeCipher } from "../../domain/ports";

const IV_BYTES = 12;
const TAG_BYTES = 16;

export class AesGcmCodeCipher implements CodeCipher {
  private readonly key: Buffer;

  constructor(base64Key: string) {
    this.key = Buffer.from(base64Key, "base64");
    if (this.key.length !== 32) throw new Error("OTP_CODE_KEY must be base64 of 32 bytes");
  }

  encrypt(code: string): string {
    const iv = randomBytes(IV_BYTES);
    const cipher = createCipheriv("aes-256-gcm", this.key, iv);
    const data = Buffer.concat([cipher.update(code, "utf8"), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), data]).toString("base64");
  }

  decrypt(ciphertext: string): string {
    const raw = Buffer.from(ciphertext, "base64");
    const iv = raw.subarray(0, IV_BYTES);
    const tag = raw.subarray(IV_BYTES, IV_BYTES + TAG_BYTES);
    const decipher = createDecipheriv("aes-256-gcm", this.key, iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([
      decipher.update(raw.subarray(IV_BYTES + TAG_BYTES)),
      decipher.final(),
    ]).toString("utf8");
  }
}

/** Without OTP_CODE_KEY no code is held, so WhatsApp stays off and every code goes by SMS. */
export const noCodeCipher: CodeCipher = {
  encrypt(): string {
    throw new Error("OTP_CODE_KEY is not configured");
  },
  decrypt(): string {
    throw new Error("OTP_CODE_KEY is not configured");
  },
};
