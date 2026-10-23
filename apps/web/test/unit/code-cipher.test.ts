// The sign-in code held for the SMS fallback (ADR 0029): AES-256-GCM with OTP_CODE_KEY.
import { randomBytes } from "node:crypto";
import { describe, expect, it } from "vitest";
import { AesGcmCodeCipher, noCodeCipher } from "../../src/infra/otp/code-cipher";

const key = () => randomBytes(32).toString("base64");

describe("AesGcmCodeCipher", () => {
  it("decrypts what it encrypted", () => {
    const cipher = new AesGcmCodeCipher(key());
    expect(cipher.decrypt(cipher.encrypt("482913"))).toBe("482913");
  });

  it("never stores the code in the clear and encrypts it differently each time", () => {
    const cipher = new AesGcmCodeCipher(key());
    const a = cipher.encrypt("482913");
    const b = cipher.encrypt("482913");
    expect(a).not.toBe(b);
    expect(a).not.toContain("482913");
    expect(Buffer.from(a, "base64").toString("utf8")).not.toContain("482913");
  });

  it("refuses a changed ciphertext", () => {
    const cipher = new AesGcmCodeCipher(key());
    const raw = Buffer.from(cipher.encrypt("482913"), "base64");
    raw[raw.length - 1] = (raw[raw.length - 1] ?? 0) ^ 1;
    expect(() => cipher.decrypt(raw.toString("base64"))).toThrow();
  });

  it("refuses a ciphertext made with another key", () => {
    const ciphertext = new AesGcmCodeCipher(key()).encrypt("482913");
    expect(() => new AesGcmCodeCipher(key()).decrypt(ciphertext)).toThrow();
  });

  it("needs a key of exactly 32 bytes", () => {
    expect(() => new AesGcmCodeCipher(randomBytes(16).toString("base64"))).toThrow(/32 bytes/);
  });

  it("without a key nothing can be held", () => {
    expect(() => noCodeCipher.encrypt("482913")).toThrow(/OTP_CODE_KEY/);
  });
});
