-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- The sender's tick (NUD-68): the recipient's phone got the task, and the recipient has seen it.
ALTER TYPE "public"."task_event_type" ADD VALUE 'delivered' AFTER 'permission_missing';--> statement-breakpoint
ALTER TYPE "public"."task_event_type" ADD VALUE 'seen' AFTER 'delivered';
