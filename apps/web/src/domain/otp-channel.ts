// Sign-in codes over WhatsApp with an SMS fallback (ADR 0029): which channel a code takes, when
// its SMS goes out, and how Twilio's statuses are kept. Pure: callers pass `now`.
import { addMs, DAY, MINUTE } from "./time";

export type OtpChannel = "whatsapp" | "sms";

/** The template's language follows the number: Turkish for +90, English for any other. */
export type OtpLanguage = "tr" | "en";

/** Without `delivered` or `read` this long after the WhatsApp send, the SMS goes out. */
export const WHATSAPP_FALLBACK_MS = 12_000;

/** A number whose WhatsApp send failed or went undelivered gets SMS directly this long. */
const NO_WHATSAPP_MEMORY_MS = 30 * DAY;

/** How long the encrypted code is held for the fallback and "SMS ile gönder". */
const CODE_LIFETIME_MS = 10 * MINUTE;

/** Send rows are kept this long: the 30 day memory and a month of costs. */
const OTP_SEND_RETENTION_MS = 31 * DAY;

export function languageFor(phone: string): OtpLanguage {
  return phone.startsWith("+90") ? "tr" : "en";
}

/** The start of the "no WhatsApp" memory that ends now; a failure exactly this old is forgotten. */
export function noWhatsAppMemoryStart(now: Date): Date {
  return addMs(now, -NO_WHATSAPP_MEMORY_MS);
}

/** Rows sent at or before this are deleted. */
export function otpRetentionStart(now: Date): Date {
  return addMs(now, -OTP_SEND_RETENTION_MS);
}

/** Codes sent at or before this are expired: their ciphertext goes, no SMS of them is sent. */
export function codeExpiredAtOrBefore(now: Date): Date {
  return addMs(now, -CODE_LIFETIME_MS);
}

/** WhatsApp sends at or before this without delivery are due for the SMS (the tick's net). */
export function fallbackDueAtOrBefore(now: Date): Date {
  return addMs(now, -WHATSAPP_FALLBACK_MS);
}

/**
 * The channel of a new code: WhatsApp when the sender is configured for it and the number had no
 * failed or undelivered WhatsApp send in the last 30 days, otherwise SMS.
 */
export function chooseChannel(
  whatsAppAvailable: boolean,
  lastWhatsAppFailureAt: Date | null,
  now: Date,
): OtpChannel {
  if (!whatsAppAvailable) return "sms";
  if (
    lastWhatsAppFailureAt !== null &&
    lastWhatsAppFailureAt.getTime() > noWhatsAppMemoryStart(now).getTime()
  ) {
    return "sms";
  }
  return "whatsapp";
}

/** Twilio statuses that say the message will never arrive: the SMS goes at once. */
export const UNDELIVERED_STATUSES = ["failed", "undelivered"] as const;

/** Twilio statuses that say the message arrived: no SMS is needed. */
export const DELIVERED_STATUSES = ["delivered", "read"] as const;

export function isUndelivered(status: string | null): boolean {
  return status === "failed" || status === "undelivered";
}

const FINAL = new Set(["delivered", "read", "failed", "undelivered", "canceled"]);

/** A status after which Twilio prices the message. */
export function isFinalStatus(status: string | null): boolean {
  return status !== null && FINAL.has(status);
}

/** How far along a message is; callbacks can arrive twice and out of order. */
function rank(status: string): number {
  switch (status) {
    case "sending":
      return 1;
    case "sent":
      return 2;
    case "delivered":
    case "undelivered":
    case "failed":
    case "canceled":
      return 3;
    case "read":
      return 4;
    default:
      // accepted, scheduled, queued and anything new.
      return 0;
  }
}

/**
 * The status to keep after a callback, or null when it changes nothing: a status only moves
 * forward, so a duplicate or late callback never moves it back from delivered or read.
 */
export function nextStatus(current: string | null, incoming: string): string | null {
  if (current === null) return incoming;
  return rank(incoming) > rank(current) ? incoming : null;
}

/** Twilio's price is a negative string in USD ("-0.0305"); what it cost, or null. */
export function priceUsd(price: string | null | undefined): number | null {
  if (price === null || price === undefined || price.trim() === "") return null;
  const n = Number(price);
  return Number.isFinite(n) ? Math.abs(n) : null;
}
