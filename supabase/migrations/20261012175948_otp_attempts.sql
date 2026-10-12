-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Wrong sign-in codes per phone (screen 6c, ADR 0011). Only the API reads it: RLS on, no grants.
CREATE TABLE "otp_attempts" (
	"phone" text PRIMARY KEY NOT NULL,
	"failures" bigint DEFAULT 0 NOT NULL,
	"locked_until" timestamp with time zone,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
alter table otp_attempts enable row level security;
