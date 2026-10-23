// Sign-in codes over WhatsApp with an SMS fallback (ADR 0029): the Send SMS hook's send, the 12 s
// fallback, "SMS ile gönder", Twilio's status callback and the tick's safety net. The decisions
// are in src/domain/otp-channel.ts; this file loads, sends and stores. Codes and ciphertexts are
// never logged.
import { randomUUID } from "node:crypto";
import {
  capReachedUntil,
  capWindowStart,
  laterOf,
  SMS_PHONE_DAILY_CAP,
  SMS_TOTAL_DAILY_CAP,
} from "../domain/cost-caps";
import {
  chooseChannel,
  codeExpiredAtOrBefore,
  fallbackDueAtOrBefore,
  isFinalStatus,
  isUndelivered,
  languageFor,
  nextStatus,
  noWhatsAppMemoryStart,
  otpRetentionStart,
  priceUsd,
  WHATSAPP_FALLBACK_MS,
  type OtpChannel,
} from "../domain/otp-channel";
import {
  addOtpCost,
  claimFallback,
  clearHeldCodes,
  completeFallback,
  dueFallbacks,
  insertOtpSend,
  lastWhatsAppFailure,
  lockOtpSendBySid,
  newestOtpSend,
  otpSendWindow,
  releaseFallback,
  updateOtpSendStatus,
} from "../infra/db/store";
import type { AppDeps } from "./deps";
import { ApiError } from "./errors";

/** When the next code to the phone may go out, over the cap per phone or in total (ADR 0024). */
export async function smsCapReachedUntil(deps: AppDeps, phone: string): Promise<Date | null> {
  const now = deps.clock.now();
  const since = capWindowStart(now);
  const perPhone = await otpSendWindow(deps.db, phone, since);
  const total = await otpSendWindow(deps.db, null, since);
  return laterOf(
    capReachedUntil(perPhone, SMS_PHONE_DAILY_CAP, now),
    capReachedUntil(total, SMS_TOTAL_DAILY_CAP, now),
  );
}

/**
 * The Send SMS hook's code to Twilio, unless a daily cap is reached (ADR 0024). Every place a
 * sign-in starts ends here, so the caps hold even for a request that skipped our API. WhatsApp
 * first unless the number had no WhatsApp in the last 30 days; a WhatsApp code is held encrypted
 * and its SMS goes after 12 s without delivery (ADR 0029). Returns whether it was sent.
 */
export async function sendSignInCode(deps: AppDeps, phone: string, code: string): Promise<boolean> {
  if (await smsCapReachedUntil(deps, phone)) return false;
  const now = deps.clock.now();
  const channel = chooseChannel(
    deps.otp.whatsApp,
    deps.otp.whatsApp
      ? await lastWhatsAppFailure(deps.db, phone, noWhatsAppMemoryStart(now))
      : null,
    now,
  );
  const id = randomUUID();
  let sent: { channel: OtpChannel; sid: string } | null = null;
  if (channel === "whatsapp") {
    try {
      const { sid } = await deps.otp.sendWhatsApp(phone, code, languageFor(phone));
      sent = { channel, sid };
    } catch (error) {
      // Twilio refused the request itself (not a delivery failure): the SMS goes instead, and
      // the number is not remembered as one without WhatsApp.
      console.error("whatsapp send failed, sending the code by SMS", error);
    }
  }
  sent ??= { channel: "sms", sid: (await deps.otp.sendSms(phone, code)).sid };
  await insertOtpSend(
    deps.db,
    {
      id,
      phone,
      sentAt: now,
      channel: sent.channel,
      twilioSid: sent.sid,
      codeCiphertext: sent.channel === "whatsapp" ? deps.otpDelivery.cipher.encrypt(code) : null,
    },
    otpRetentionStart(now),
  );
  if (sent.channel === "whatsapp") {
    deps.otpDelivery.waitUntil(fallBackAfterTimeout(deps, id));
  }
  return true;
}

async function fallBackAfterTimeout(deps: AppDeps, id: string): Promise<void> {
  await deps.otpDelivery.delay.wait(WHATSAPP_FALLBACK_MS);
  try {
    await fallBackToSms(deps, id, false);
  } catch (error) {
    // The tick's safety net tries again while the code is held.
    console.error("otp sms fallback failed", error);
  }
}

/**
 * Sends the SMS of a WhatsApp code once, whichever path comes first: the 12 s timer, a failed
 * status, the tick, or "SMS ile gönder" (`evenIfDelivered`). Returns whether this call sent it.
 */
async function fallBackToSms(
  deps: AppDeps,
  id: string,
  evenIfDelivered: boolean,
): Promise<boolean> {
  const now = deps.clock.now();
  const claimed = await claimFallback(
    deps.db,
    id,
    now,
    codeExpiredAtOrBefore(now),
    evenIfDelivered,
  );
  if (!claimed) return false;
  let sid: string;
  try {
    const code = deps.otpDelivery.cipher.decrypt(claimed.codeCiphertext);
    ({ sid } = await deps.otp.sendSms(claimed.phone, code));
  } catch (error) {
    await releaseFallback(deps.db, id);
    throw error;
  }
  await completeFallback(deps.db, id, sid);
  return true;
}

/** The channel of the newest code to the phone; "sms" for test numbers, which never reach the hook. */
export async function channelOfLatestCode(deps: AppDeps, phone: string): Promise<OtpChannel> {
  const now = deps.clock.now();
  const row = await newestOtpSend(deps.db, phone, codeExpiredAtOrBefore(now));
  if (row === null || row.fallbackAt !== null) return "sms";
  return row.channel;
}

/**
 * "SMS ile gönder": the newest code to the phone, again by SMS, if it is held and has not gone by
 * SMS yet. It never creates a code, so it adds nothing to the caps (ADR 0029).
 */
export async function resendCodeBySms(deps: AppDeps, phone: string): Promise<void> {
  const now = deps.clock.now();
  const row = await newestOtpSend(deps.db, phone, codeExpiredAtOrBefore(now));
  const unavailable = new ApiError("otp_resend_unavailable", "no code can go by SMS now");
  if (row === null) throw unavailable;
  let sent: boolean;
  try {
    sent = await fallBackToSms(deps, row.id, true);
  } catch (error) {
    console.error("otp sms resend failed", error);
    throw new ApiError("upstream_failed", "could not send the code");
  }
  if (!sent) throw unavailable;
}

/** A correct code signed in: no code of the phone is held any more. */
export async function forgetHeldCodes(deps: AppDeps, phone: string): Promise<void> {
  await clearHeldCodes(deps.db, { phone });
}

export interface TwilioStatus {
  sid: string;
  status: string;
  errorCode: string | null;
  /** Twilio's `Price` when the callback carries it. */
  price: string | null;
}

/**
 * Twilio's status callback (ADR 0029). Idempotent: a status only moves forward, so a duplicate or
 * late callback changes nothing. A failed or undelivered WhatsApp message sends the SMS at once.
 * The first final status of a message adds its price, when Twilio has one.
 */
export async function recordTwilioStatus(deps: AppDeps, update: TwilioStatus): Promise<void> {
  const result = await deps.db.transaction(async (tx) => {
    const found = await lockOtpSendBySid(tx, update.sid);
    if (!found) return null;
    const { row, fallback } = found;
    const current = fallback ? row.fallbackStatus : row.status;
    const next = nextStatus(current, update.status);
    if (next === null) return null;
    await updateOtpSendStatus(
      tx,
      row.id,
      fallback ? { fallbackStatus: next } : { status: next, errorCode: update.errorCode },
    );
    return {
      id: row.id,
      becameFinal: isFinalStatus(next) && !isFinalStatus(current),
      fallBack: !fallback && row.channel === "whatsapp" && isUndelivered(next),
    };
  });
  if (result === null) return;
  if (result.fallBack) {
    try {
      await fallBackToSms(deps, result.id, false);
    } catch (error) {
      console.error("otp sms fallback failed", error);
    }
  }
  if (result.becameFinal) {
    const usd = priceUsd(update.price) ?? (await deps.otp.priceOf(update.sid).catch(() => null));
    if (usd !== null) await addOtpCost(deps.db, result.id, usd);
  }
}

/**
 * The tick's part (ADR 0029): the SMS of any WhatsApp code older than 12 s without delivery or
 * fallback, in case the function stopped before its timer ran; then held codes past their life
 * are cleared. Returns how many SMS went out.
 */
export async function runOtpSafetyNet(deps: AppDeps): Promise<number> {
  const now = deps.clock.now();
  let sent = 0;
  for (const id of await dueFallbacks(
    deps.db,
    fallbackDueAtOrBefore(now),
    codeExpiredAtOrBefore(now),
  )) {
    try {
      if (await fallBackToSms(deps, id, false)) sent++;
    } catch (error) {
      console.error("otp sms fallback failed", error);
    }
  }
  await clearHeldCodes(deps.db, { expiredAtOrBefore: codeExpiredAtOrBefore(now) });
  return sent;
}
