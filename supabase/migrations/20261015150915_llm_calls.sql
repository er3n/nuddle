-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Every LLM call with its input, output and outcome (ADR 0019). Only the API reads it: RLS on, no grants.
CREATE TYPE "public"."llm_call_kind" AS ENUM('extraction', 'plan');--> statement-breakpoint
CREATE TABLE "llm_calls" (
	"id" uuid PRIMARY KEY NOT NULL,
	"kind" "llm_call_kind" NOT NULL,
	"person_id" uuid NOT NULL,
	"task_id" uuid,
	"write_key" uuid,
	"plan_version" bigint,
	"adopted_version" bigint,
	"input" jsonb NOT NULL,
	"model" text,
	"system" text,
	"prompt" text,
	"output" jsonb,
	"outcome" text NOT NULL,
	"error" text,
	"latency_ms" bigint NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "llm_calls" ADD CONSTRAINT "llm_calls_person_id_people_id_fk" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "llm_calls" ADD CONSTRAINT "llm_calls_task_id_tasks_id_fk" FOREIGN KEY ("task_id") REFERENCES "public"."tasks"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "llm_calls_task_idx" ON "llm_calls" USING btree ("task_id");--> statement-breakpoint
CREATE INDEX "llm_calls_person_idx" ON "llm_calls" USING btree ("person_id");;
alter table llm_calls enable row level security;
