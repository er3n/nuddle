// The cloud Auth settings (supabase/cloud/auth.json, applied by supabase/cloud/configure.sh) never
// enable test numbers, which would let anyone reading this public repo sign in (ADR 0011), and
// send codes through our hook on the production host.
import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";

const path = new URL("../../../../supabase/cloud/auth.json", import.meta.url);

describe("cloud auth config", () => {
  it("has no test numbers and points the Send SMS hook at production", async () => {
    const config = JSON.parse(await readFile(path, "utf8")) as Record<string, unknown>;
    expect(config.sms_test_otp).toBe("");
    expect(config).not.toHaveProperty("sms_test_otp_valid_until");
    expect(config.external_phone_enabled).toBe(true);
    expect(config.hook_send_sms_enabled).toBe(true);
    expect(config.hook_send_sms_uri).toBe("https://nuddle.app/api/internal/otp-hook");
    // The secret is added from ~/.nuddle/prod at apply time, never committed.
    expect(config).not.toHaveProperty("hook_send_sms_secrets");
  });
});
