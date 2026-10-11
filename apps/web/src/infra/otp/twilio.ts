// OtpSender over Twilio Programmable Messaging (SMS provider decided in NUD-8). The Send SMS hook
// calls this through /api/internal/otp-hook. WhatsApp templates come later (ADR 0011).
import type { OtpSender } from "../../domain/ports";

export interface TwilioConfig {
  accountSid: string;
  authToken: string;
  /** A Messaging Service SID (MG...) or a sender number in E.164. */
  from: string;
  /** Android SMS Retriever app hash (11 characters), appended so the app reads the code. */
  smsRetrieverHash: string | null;
}

/** The SMS text. TODO(open-questions#26): the wording is a default. */
export function otpMessage(code: string, smsRetrieverHash: string | null): string {
  const text = `dürt kodun: ${code}`;
  return smsRetrieverHash === null ? text : `${text}\n\n${smsRetrieverHash}`;
}

export class TwilioOtpSender implements OtpSender {
  constructor(
    private readonly config: TwilioConfig,
    private readonly fetchFn: typeof fetch = fetch,
  ) {}

  async send(phone: string, code: string): Promise<void> {
    const { accountSid, authToken, from } = this.config;
    const body = new URLSearchParams({
      To: phone,
      Body: otpMessage(code, this.config.smsRetrieverHash),
      ...(from.startsWith("MG") ? { MessagingServiceSid: from } : { From: from }),
    });
    const res = await this.fetchFn(
      `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(accountSid)}/Messages.json`,
      {
        method: "POST",
        headers: {
          Authorization: `Basic ${Buffer.from(`${accountSid}:${authToken}`).toString("base64")}`,
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body,
      },
    );
    if (!res.ok) {
      // Twilio's error body names the problem without echoing the code.
      throw new Error(`twilio: ${String(res.status)} ${await res.text()}`);
    }
  }
}

/** Without provider credentials the hook fails instead of logging codes. */
export const unconfiguredOtpSender: OtpSender = {
  send(): Promise<void> {
    return Promise.reject(new Error("OTP sending is not configured (TWILIO_* env)"));
  },
};
