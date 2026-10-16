# 0011 Phone number is the only identity

Status: Accepted (2026-10-10, auth provider updated 2026-10-11).

## Decision
- E.164 phone number is the identity. Sign-up and sign-in are one flow.
- Supabase Auth phone OTP. Its Send SMS hook calls `/api/internal/otp-hook`, which sends the code via a WhatsApp authentication template, falling back to SMS. The MVP may start SMS only.
- 6 digit code, limited attempts then a 15 minute wait (SMS pumping protection). Android uses the SMS Retriever API so no SMS permission is needed.
- Invites are bound to the invitee's number, so a lost link still reaches them when they register.
- No social login, so the Sign in with Apple requirement is never triggered on iOS.
- Test numbers with fixed codes are configured in `supabase/config.toml` for local and E2E only. They must never be enabled in the cloud project: the repo is public. The one exception is the Play review number, kept outside the repo (ADR 0022).

## Rejected
- **Email or password accounts:** extra friction for the target users (families).
- **Social login:** see above.
- **Hand-written OTP + session tokens:** Supabase already does it; we only own delivery.
