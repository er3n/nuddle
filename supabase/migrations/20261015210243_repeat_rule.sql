-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Repeating tasks (ADR 0021): the rule on every occurrence and the id its occurrences share.
ALTER TABLE "tasks" ADD COLUMN "repeat" jsonb;--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "series_id" uuid;