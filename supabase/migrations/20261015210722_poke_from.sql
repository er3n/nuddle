-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- The recipient's earliest poke time on a by task and its event (NUD-78).
ALTER TYPE "public"."task_event_type" ADD VALUE 'start_set' AFTER 'seen';--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "poke_from" timestamp with time zone;