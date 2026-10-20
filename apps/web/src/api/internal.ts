// Internal endpoints behind shared secrets (ADR 0003, 0007, 0011). Not part of the public
// contract.
import { timingSafeEqual } from "node:crypto";
import { Hono } from "hono";
import { Webhook } from "standardwebhooks";
import { z } from "zod";
import type { AppDeps } from "./deps";
import { ApiError } from "./errors";
import type { AppEnv } from "./routes";
import { SLUG } from "../domain/announcements";
import {
  announce,
  deleteAccountByPhone,
  deletionPreview,
  runTick,
  sendSignInCode,
} from "./services";

function sameSecret(given: string, expected: string): boolean {
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

const SendSmsHookPayload = z.object({
  user: z.object({ phone: z.string().min(1) }),
  sms: z.object({ otp: z.string().regex(/^\d{4,10}$/) }),
});

const DeleteAccountRequest = z.object({
  phone: z.string().regex(/^\+\d{8,15}$/),
  /** Without it nothing changes: the answer says what would be deleted. */
  confirm: z.boolean().default(false),
});

const AnnounceRequest = z.object({
  announcement: z.object({
    slug: z.string().regex(SLUG),
    title: z.string(),
    summary: z.string(),
    body: z.string(),
    button: z.object({ label: z.string(), target: z.string() }).nullable().default(null),
  }),
  /** Phone numbers, or "*" for every live account. */
  to: z.union([z.literal("*"), z.array(z.string().regex(/^\+\d{8,15}$/)).min(1)]),
  /** Without it nothing changes: the answer says who would get it. */
  confirm: z.boolean().default(false),
});

export function internalRoutes(deps: AppDeps): Hono<AppEnv> {
  const r = new Hono<AppEnv>();

  const requireTickSecret = (header: string | undefined) => {
    if (!sameSecret(header ?? "", `Bearer ${deps.tickSecret}`)) {
      throw new ApiError("unauthorized", "bad tick secret");
    }
  };

  // pg_cron via pg_net every minute in the cloud; `mise run tick` locally.
  r.post("/tick", async (c) => {
    requireTickSecret(c.req.header("authorization"));
    return c.json({ handled: await runTick(deps) });
  });

  // `mise run account:delete`: a deletion asked for by email without the app (NUD-88). The human
  // runs it; the same secret as the tick, both are server-only operations.
  r.post("/delete-account", async (c) => {
    requireTickSecret(c.req.header("authorization"));
    const parsed = DeleteAccountRequest.safeParse(await c.req.json().catch(() => null));
    if (!parsed.success) throw new ApiError("validation_failed", "phone must be E.164");
    const { phone, confirm } = parsed.data;
    const account = confirm
      ? await deleteAccountByPhone(deps, phone)
      : await deletionPreview(deps, phone);
    return c.json({ account, deleted: confirm && account !== null });
  });

  // `mise run announce`: a message from Nuddle to chosen people or everyone (ADR 0025). The human
  // runs it against production.
  r.post("/announce", async (c) => {
    requireTickSecret(c.req.header("authorization"));
    const parsed = AnnounceRequest.safeParse(await c.req.json().catch(() => null));
    if (!parsed.success) throw new ApiError("validation_failed", parsed.error.message);
    const { announcement, to, confirm } = parsed.data;
    return c.json(await announce(deps, announcement, to, confirm));
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
      if (!(await sendSignInCode(deps, phone, payload.sms.otp))) {
        console.warn("otp send refused: daily SMS cap reached");
        return c.json({ error: { http_code: 429, message: "too many codes" } }, 429);
      }
    } catch (error) {
      console.error("otp send failed", error);
      return c.json({ error: { http_code: 500, message: "could not send the code" } }, 500);
    }
    return c.json({});
  });

  return r;
}
