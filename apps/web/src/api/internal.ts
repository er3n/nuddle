// Internal endpoints behind shared secrets (ADR 0003, 0007, 0011). Not part of the public
// contract.
import { timingSafeEqual } from "node:crypto";
import { Hono } from "hono";
import { Webhook } from "standardwebhooks";
import { z } from "zod";
import type { AppDeps } from "./deps";
import { ApiError } from "./errors";
import type { AppEnv } from "./routes";
import { runTick } from "./services";

function sameSecret(given: string, expected: string): boolean {
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

const SendSmsHookPayload = z.object({
  user: z.object({ phone: z.string().min(1) }),
  sms: z.object({ otp: z.string().regex(/^\d{4,10}$/) }),
});

export function internalRoutes(deps: AppDeps): Hono<AppEnv> {
  const r = new Hono<AppEnv>();

  // pg_cron via pg_net every minute in the cloud; `mise run tick` locally.
  r.post("/tick", async (c) => {
    if (!sameSecret(c.req.header("authorization") ?? "", `Bearer ${deps.tickSecret}`)) {
      throw new ApiError("unauthorized", "bad tick secret");
    }
    return c.json({ handled: await runTick(deps) });
  });

  // Supabase Auth Send SMS hook, signed with Standard Webhooks. Errors use the shape Supabase Auth
  // expects so it shows the sign-in a failure instead of a success.
  r.post("/otp-hook", async (c) => {
    const raw = await c.req.text();
    let payload: z.infer<typeof SendSmsHookPayload>;
    try {
      const wh = new Webhook(deps.smsHookSecret.replace(/^v1,whsec_/, ""));
      payload = SendSmsHookPayload.parse(wh.verify(raw, c.req.header()));
    } catch {
      return c.json({ error: { http_code: 401, message: "invalid hook signature" } }, 401);
    }
    const phone = payload.user.phone.startsWith("+")
      ? payload.user.phone
      : `+${payload.user.phone}`;
    try {
      await deps.otp.send(phone, payload.sms.otp);
    } catch (error) {
      console.error("otp send failed", error);
      return c.json({ error: { http_code: 500, message: "could not send the code" } }, 500);
    }
    return c.json({});
  });

  return r;
}
