// OtpSender over Twilio Programmable Messaging (SMS provider decided in NUD-8, WhatsApp in ADR
// 0029). The Send SMS hook calls this through /api/internal/otp-hook; Twilio reports every
// message's status to /api/internal/twilio-status, signed with the auth token.
import { createHmac, timingSafeEqual } from "node:crypto";
import type { OtpLanguage } from "../../domain/otp-channel";
import type { OtpSender, SentMessage } from "../../domain/ports";

interface TwilioWhatsAppConfig {
  /** The WhatsApp sender number in E.164, sent as `whatsapp:<number>`. */
  from: string;
  /** Content SIDs (HX...) of the authentication templates per language. */
  templates: Record<OtpLanguage, string>;
}

export interface TwilioConfig {
  accountSid: string;
  authToken: string;
  /** A Messaging Service SID (MG...) or a sender number in E.164. */
  from: string;
  /** Android SMS Retriever app hash (11 characters), appended so the app reads the code. */
  smsRetrieverHash: string | null;
  /** The public URL of /api/internal/twilio-status; null sends no status callbacks. */
  statusCallback: string | null;
  /** Null until the WhatsApp sender and templates are approved: SMS only, as before. */
  whatsApp: TwilioWhatsAppConfig | null;
}

/** The SMS text. TODO(open-questions#26): the wording is a default. */
export function otpMessage(code: string, smsRetrieverHash: string | null): string {
  const text = `dürt kodun: ${code}`;
  return smsRetrieverHash === null ? text : `${text}\n\n${smsRetrieverHash}`;
}

/**
 * Twilio's request signature: HMAC-SHA1 with the auth token over the full URL Twilio called and
 * every POST parameter, sorted by name, name and value appended (twilio.com/docs/usage/security).
 */
export function twilioSignature(
  authToken: string,
  url: string,
  params: Record<string, string>,
): string {
  const data = Object.keys(params)
    .sort()
    .reduce((acc, key) => acc + key + (params[key] ?? ""), url);
  return createHmac("sha1", authToken).update(data, "utf8").digest("base64");
}

/** Whether `X-Twilio-Signature` matches, compared in constant time. */
export function isTwilioSignatureValid(
  authToken: string,
  url: string,
  params: Record<string, string>,
  signature: string,
): boolean {
  const expected = Buffer.from(twilioSignature(authToken, url, params));
  const given = Buffer.from(signature);
  return expected.length === given.length && timingSafeEqual(expected, given);
}

export class TwilioOtpSender implements OtpSender {
  readonly whatsApp: boolean;

  constructor(
    private readonly config: TwilioConfig,
    private readonly fetchFn: typeof fetch = fetch,
  ) {
    this.whatsApp = config.whatsApp !== null;
  }

  sendWhatsApp(phone: string, code: string, language: OtpLanguage): Promise<SentMessage> {
    const whatsApp = this.config.whatsApp;
    if (whatsApp === null) return Promise.reject(new Error("WhatsApp is not configured"));
    return this.create({
      To: `whatsapp:${phone}`,
      From: `whatsapp:${whatsApp.from}`,
      ContentSid: whatsApp.templates[language],
      ContentVariables: JSON.stringify({ "1": code }),
    });
  }

  sendSms(phone: string, code: string): Promise<SentMessage> {
    const { from } = this.config;
    return this.create({
      To: phone,
      Body: otpMessage(code, this.config.smsRetrieverHash),
      ...(from.startsWith("MG") ? { MessagingServiceSid: from } : { From: from }),
    });
  }

  async priceOf(sid: string): Promise<number | null> {
    const res = await this.fetchFn(this.messagesUrl(`/${encodeURIComponent(sid)}.json`), {
      headers: { Authorization: this.authorization() },
    });
    if (!res.ok) throw new Error(`twilio: ${String(res.status)} ${await res.text()}`);
    const body = (await res.json()) as { price?: string | null };
    const price = body.price ?? null;
    return price === null ? null : Math.abs(Number(price));
  }

  private async create(params: Record<string, string>): Promise<SentMessage> {
    const body = new URLSearchParams(params);
    if (this.config.statusCallback !== null) {
      body.set("StatusCallback", this.config.statusCallback);
    }
    const res = await this.fetchFn(this.messagesUrl(".json"), {
      method: "POST",
      headers: {
        Authorization: this.authorization(),
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body,
    });
    if (!res.ok) {
      // Twilio's error body names the problem without echoing the code.
      throw new Error(`twilio: ${String(res.status)} ${await res.text()}`);
    }
    const { sid } = (await res.json()) as { sid: string };
    return { sid };
  }

  private messagesUrl(suffix: string): string {
    const account = encodeURIComponent(this.config.accountSid);
    return `https://api.twilio.com/2010-04-01/Accounts/${account}/Messages${suffix}`;
  }

  private authorization(): string {
    const { accountSid, authToken } = this.config;
    return `Basic ${Buffer.from(`${accountSid}:${authToken}`).toString("base64")}`;
  }
}

const notConfigured = () =>
  Promise.reject(new Error("OTP sending is not configured (TWILIO_* env)"));

/** Without provider credentials the hook fails instead of logging codes. */
export const unconfiguredOtpSender: OtpSender = {
  whatsApp: false,
  sendWhatsApp: notConfigured,
  sendSms: notConfigured,
  priceOf: () => Promise.resolve(null),
};
