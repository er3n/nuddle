-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- The sender's example photo on a task and its event (NUD-138); never proof.
ALTER TYPE "public"."task_event_type" ADD VALUE 'example_photo_set' AFTER 'start_set';--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "example_photo_path" text;
