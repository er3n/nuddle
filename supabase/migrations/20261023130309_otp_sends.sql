-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Sign-in codes over WhatsApp with an SMS fallback (ADR 0029): `sms_sends` becomes `otp_sends`,
-- one row per code whatever the channel. The rows so far all went by SMS. Only the API reads this
-- table, and it ships together with the rename (no users before the beta). RLS stays on, no
-- grants.
-- squawk-ignore renaming-table
ALTER TABLE "sms_sends" RENAME TO "otp_sends";--> statement-breakpoint
-- squawk-ignore renaming-object
ALTER INDEX "sms_sends_sent_at_idx" RENAME TO "otp_sends_sent_at_idx";--> statement-breakpoint
-- squawk-ignore renaming-object
ALTER INDEX "sms_sends_phone_idx" RENAME TO "otp_sends_phone_idx";--> statement-breakpoint
-- squawk-ignore renaming-object
ALTER TABLE "otp_sends" RENAME CONSTRAINT "sms_sends_pkey" TO "otp_sends_pkey";--> statement-breakpoint
-- A constant default needs no table rewrite (Postgres 11+), and the table holds a day of rows.
-- squawk-ignore adding-field-with-default
ALTER TABLE "otp_sends" ADD COLUMN "channel" text DEFAULT 'sms' NOT NULL;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "twilio_sid" text;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "status" text;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "error_code" text;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "fallback_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "fallback_sid" text;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "fallback_status" text;--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "cost_usd" numeric(12, 6);--> statement-breakpoint
ALTER TABLE "otp_sends" ADD COLUMN "code_ciphertext" text;--> statement-breakpoint
-- The new columns are all null, so building the unique indexes is instant (UNIQUE cannot be NOT
-- VALID); a small table that only the hook writes.
-- squawk-ignore disallowed-unique-constraint, constraint-missing-not-valid
ALTER TABLE "otp_sends" ADD CONSTRAINT "otp_sends_twilio_sid_unique" UNIQUE("twilio_sid");--> statement-breakpoint
-- squawk-ignore disallowed-unique-constraint, constraint-missing-not-valid
ALTER TABLE "otp_sends" ADD CONSTRAINT "otp_sends_fallback_sid_unique" UNIQUE("fallback_sid");
