# 0029 Sign-in codes over WhatsApp via Twilio, SMS as fallback

Status: Accepted (2026-10-23). Decided by the human in NUD-147. Refines ADRs 0011, 0014 and 0024.

## Context
ADR 0011 decided "WhatsApp authentication template first, SMS fallback" and let the MVP start with SMS only. ADR 0014 chose Twilio for SMS and noted that WhatsApp templates can use the same vendor. Since 2026-10-14 every code is an SMS from a US number (open-questions #45). Most target users in Turkey use WhatsApp every day, a WhatsApp authentication message costs less than an SMS to Turkey, and it is not filtered by Turkish carriers the way a foreign SMS sender can be.

Constraints:
- Supabase Auth calls the Send SMS hook and waits for the answer for a few seconds (about 5 s). The fallback cannot wait inside that request.
- `sms_max_frequency` is 60 s (ADR 0024), so Supabase issues no second code within a minute. A switch to SMS after a few seconds has to send the same code.
- WhatsApp authentication templates have Meta's fixed text ("123456 is your verification code" and its translations), one variable (the code) and a copy-code button. One-tap and zero-tap autofill are set on the template in WhatsApp Manager with up to five Android apps (package name and the same 11-character signing hash as the SMS Retriever); Twilio's Content API creates only the copy-code template.

## Decision
- **One sender.** The existing Twilio US number becomes a WhatsApp sender with the display name **Nuddle**, under the Meta Business portfolio of Dokukoza. Meta checks the name against `nuddle.app`. One sender serves every market, so the global launch needs no new approval; the message body is Meta's fixed text in the template's language, so the brand rule (dürt in Turkey) is not broken by it.
- **Two templates**, Turkish and English, each with the copy-code button, the security note, a 10 minute expiry note, and in WhatsApp Manager one-tap/zero-tap autofill for `com.dokukoza.nuddle` (`FdXmcSJuarV`), `app.nuddle.debug` (local debug key hash) and, while testers still run it, `app.nuddle`. The language follows the number: `+90` gets Turkish, any other number English. Until the global launch only `+90` reaches the hook (#45).
- **Channel choice in the hook.** The hook counts the caps (both channels together), then sends the WhatsApp template, unless the number had a WhatsApp send that failed or went undelivered in the last **30 days**. That number gets the SMS directly. Nothing else is kept about channels: the memory is the send rows themselves.
- **`sms_sends` becomes `otp_sends`.** One row per code the hook sent, whatever the channel: `phone`, `sent_at`, `channel` (`whatsapp` or `sms`), `twilio_sid`, `status` (Twilio's last status), `error_code`, `fallback_at` (when the SMS of the same code went out), `fallback_sid` and `fallback_status` (that SMS's MessageSid and last status, so its callback finds the row and its price is added once), `cost_usd` (Twilio's price for the WhatsApp message and the SMS together, filled when the final status arrives, null while Twilio has not priced it), and `code_ciphertext`. A code with its fallback is one row and counts once toward the caps. Rows live 31 days (the 30 day memory and a month of costs), then the hook deletes them; deleting an account deletes its phone's rows, as before.
- **A WhatsApp request Twilio refuses itself** (an API error, not a delivery status) sends the SMS at once; that row is an `sms` row, so an outage on our side never marks a number as one without WhatsApp.
- **The code is kept encrypted for at most 10 minutes**, so the fallback and "SMS ile gönder" send the same code. AES-256-GCM with `OTP_CODE_KEY` (Vercel env only, never in the repo); the ciphertext is cleared when the SMS goes out, when a correct code signs in, and by the tick after 10 minutes. Logs never carry the code or the ciphertext.
- **Fallback, whichever comes first, once.** The send goes out with the SMS sent at most once per row (`fallback_at` set in the same update that claims it):
  - *Status callback:* `POST /api/internal/twilio-status` (Twilio's `X-Twilio-Signature`, checked against the public URL, `PUBLIC_BASE_URL` or `https://nuddle.app`, and `TWILIO_AUTH_TOKEN`; 403 otherwise) records the status. On `failed` or `undelivered` (for example 63003, not on WhatsApp) it sends the SMS at once. Duplicate or out-of-order callbacks change nothing: a status never moves back from `delivered` or `read`.
  - *Timeout:* the hook hands a 12 s timer to Vercel's `waitUntil`, so it answers Supabase at once and the function lives on. After **12 s** without `delivered` or `read` the SMS goes out. The wait is the injected `Delay` port; domain code never reads the wall clock (ADR 0009).
  - *Safety net:* the minute tick (ADR 0007) sends the SMS for any WhatsApp row older than 12 s that has neither a delivery nor a fallback and still holds its code, in case the function was stopped before its timer ran.
- **"SMS ile gönder".** `POST /v1/auth/otp/sms { phone }` sends the SMS of the code already sent to that phone, if one is held and has not gone by SMS yet. It never creates a code, so it adds nothing to the caps and opens no pumping path: each code reaches SMS at most once. Otherwise `otp_resend_unavailable` (DRT-1011, 409), and the app offers "Yeni kod gönder" when its countdown allows.
- **The app learns the channel.** `/v1/auth/otp` answers `{ ok: true, channel: "whatsapp" | "sms" }` (`OtpSent`, also the answer of `/v1/auth/otp/sms`) (the hook stores it, the API reads the newest row for the phone). Anyone can already learn whether a number uses WhatsApp from WhatsApp itself, so this tells nothing new.
- **Android.** Before asking for a code the app does the WhatsApp OTP SDK handshake (Meta's `whatsapp-otp-android-sdk`) when WhatsApp is installed, and listens for `com.whatsapp.otp.OTP_RETRIEVED`. A code from WhatsApp fills and submits like the SMS Retriever, which stays as it is. Screen 0d shows the channel and "SMS ile gönder" (screens.md).
- **The Play review number is unchanged** (ADR 0022). Supabase never calls the hook for a test number, so nothing is sent on any channel.

## Consequences
- A person without WhatsApp waits at most about 12 s the first time, and then never for 30 days.
- A slow WhatsApp delivery can bring both a WhatsApp message and an SMS with the same code; the code is the same, so either works. The cost is both messages.
- The server holds a usable code for up to 10 minutes. Someone with both the database and `OTP_CODE_KEY` could sign in as a person whose code is in flight; with both they could do far more already.
- Twilio Geo permissions and balance alerts (ADR 0024) now cover WhatsApp too. The human checks the WhatsApp price to Turkey in Twilio's pricing page (open-questions #45).
- E2E uses the local test numbers with fixed codes and never reaches Twilio; channel choice, fallback and the webhook are covered by unit, HTTP and scenario tests with fake senders, a fake delay and the injected clock.

## Rejected
- **Fallback inside the hook request** (wait for the callback, then answer): over Supabase's hook timeout.
- **Fallback only from the minute tick:** up to a minute of waiting for a person without WhatsApp.
- **A new code for "SMS ile gönder"** (`sms_max_frequency` lowered to 10 s): every press would count toward the 5 daily codes, and a shorter frequency weakens Supabase's own pumping limit.
- **A separate table for "no WhatsApp" numbers:** the send rows already say it.
- **Display name "dürt" or "Dokukoza":** "dürt" has no proof on the website and needs a second sender or a new approval at the global launch; "Dokukoza" is not a name users know.
- **Twilio Verify:** a second Twilio product with its own codes, while Supabase Auth already owns the code.
