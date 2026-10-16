-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Cost guardrails (ADR 0024): tokens and cost per LLM call, and every sign-in code sent, for the
-- daily caps per phone and in total. Only the API reads sms_sends: RLS on, no grants.
CREATE TABLE "sms_sends" (
	"id" uuid PRIMARY KEY NOT NULL,
	"phone" text NOT NULL,
	"sent_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
ALTER TABLE "llm_calls" ADD COLUMN "prompt_tokens" bigint;--> statement-breakpoint
ALTER TABLE "llm_calls" ADD COLUMN "completion_tokens" bigint;--> statement-breakpoint
ALTER TABLE "llm_calls" ADD COLUMN "cost_usd" double precision;--> statement-breakpoint
CREATE INDEX "sms_sends_sent_at_idx" ON "sms_sends" USING btree ("sent_at");--> statement-breakpoint
CREATE INDEX "sms_sends_phone_idx" ON "sms_sends" USING btree ("phone");;
alter table sms_sends enable row level security;
