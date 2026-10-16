// `supabase/cloud/configure.sh auth` adds the one Play review number from ~/.nuddle/prod to the
// cloud Auth settings and never prints it (ADR 0022). The Management API is replaced by a fake
// curl on PATH that keeps the request body and answers like Supabase does.
import { spawnSync } from "node:child_process";
import { chmod, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const script = fileURLToPath(new URL("../../../../supabase/cloud/configure.sh", import.meta.url));

// Writes the PATCH body to $CAPTURE/body.json and answers every call with the stored config.
const fakeCurl = `#!/usr/bin/env bash
out=""; data=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) out="$2"; shift ;;
    --data-binary) data="\${2#@}"; shift ;;
  esac
  shift
done
if [[ -n "$data" ]]; then cp "$data" "$CAPTURE/body.json"; fi
cp "$CAPTURE/body.json" "$out"
printf 200
`;

let prod: string;
let bin: string;
let capture: string;

beforeEach(async () => {
  prod = await mkdtemp(join(tmpdir(), "nuddle-prod-"));
  bin = await mkdtemp(join(tmpdir(), "nuddle-bin-"));
  capture = await mkdtemp(join(tmpdir(), "nuddle-capture-"));
  await writeFile(join(prod, "SUPABASE_ACCESS_TOKEN"), "test-token");
  await writeFile(join(prod, "SEND_SMS_HOOK_SECRET"), "v1,whsec_dGVzdA==");
  await writeFile(join(bin, "curl"), fakeCurl);
  await chmod(join(bin, "curl"), 0o755);
});

afterEach(async () => {
  await Promise.all([prod, bin, capture].map((dir) => rm(dir, { recursive: true, force: true })));
});

function applyAuth() {
  return spawnSync("bash", [script, "auth"], {
    encoding: "utf8",
    env: {
      ...process.env,
      NUDDLE_PROD_DIR: prod,
      CAPTURE: capture,
      PATH: `${bin}:${process.env.PATH ?? ""}`,
    },
  });
}

async function sentBody() {
  return JSON.parse(await readFile(join(capture, "body.json"), "utf8")) as Record<string, unknown>;
}

describe("configure.sh auth", () => {
  it("sends no test number when there is no review file", async () => {
    const run = applyAuth();
    expect(run.status).toBe(0);
    const body = await sentBody();
    expect(body.sms_test_otp).toBe("");
    expect(body).not.toHaveProperty("sms_test_otp_valid_until");
    expect(run.stdout).toContain('"sms_test_numbers": 0');
  });

  it("adds the review number and never prints it", async () => {
    await writeFile(join(prod, "PLAY_REVIEW_OTP"), "905550001122=246810\n");
    const run = applyAuth();
    expect(run.status).toBe(0);
    const body = await sentBody();
    expect(body.sms_test_otp).toBe("905550001122=246810");
    // Supabase refuses a test number without an expiry; it is 90 days from the run.
    const days = (Date.parse(String(body.sms_test_otp_valid_until)) - Date.now()) / 86_400_000;
    expect(days).toBeGreaterThan(89);
    expect(days).toBeLessThanOrEqual(90);
    expect(run.stdout).toContain('"sms_test_numbers": 1');
    const printed = run.stdout + run.stderr;
    expect(printed).not.toContain("905550001122");
    expect(printed).not.toContain("246810");
  });

  it("refuses a malformed review file before calling the API", async () => {
    await writeFile(join(prod, "PLAY_REVIEW_OTP"), "+90 555 000 11 22=1234");
    const run = applyAuth();
    expect(run.status).not.toBe(0);
    expect(run.stderr).toContain("PLAY_REVIEW_OTP must be");
    expect(run.stderr).not.toContain("1234");
    await expect(readFile(join(capture, "body.json"), "utf8")).rejects.toThrow();
  });
});
