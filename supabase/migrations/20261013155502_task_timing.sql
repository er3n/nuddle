-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- A task is done by its deadline (`by`) or is a moment (`at`, remind_at) whose deadline is a grace after it (ADR 0017).
-- Existing tasks are all `by`; a constant default adds the column without a table rewrite.
CREATE TYPE "public"."timing" AS ENUM('at', 'by');--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "timing" "timing" DEFAULT 'by' NOT NULL;--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "remind_at" timestamp with time zone;
