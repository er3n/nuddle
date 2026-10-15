-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- The rolling poke profile the planner reads (ADR 0020), and its LLM call kind. Nullable, no rewrite.
ALTER TYPE "public"."llm_call_kind" ADD VALUE 'profile' AFTER 'plan';--> statement-breakpoint
ALTER TABLE "people" ADD COLUMN "poke_profile" text;--> statement-breakpoint
ALTER TABLE "people" ADD COLUMN "poke_profile_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "people" ADD COLUMN "poke_profile_tasks" bigint DEFAULT 0 NOT NULL;