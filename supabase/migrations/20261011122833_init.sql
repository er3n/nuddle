-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

CREATE TYPE "public"."device_platform" AS ENUM('android', 'ios');--> statement-breakpoint
CREATE TYPE "public"."edge_status" AS ENUM('pending', 'active', 'removed');--> statement-breakpoint
CREATE TYPE "public"."edge_type" AS ENUM('spouse', 'parent', 'child', 'sibling', 'roommate', 'other');--> statement-breakpoint
CREATE TYPE "public"."finish_mode" AS ENUM('done', 'photo');--> statement-breakpoint
CREATE TYPE "public"."invite_status" AS ENUM('open', 'accepted', 'declined', 'revoked', 'expired');--> statement-breakpoint
CREATE TYPE "public"."poke_level" AS ENUM('SILENT', 'SOUND', 'FULL_SCREEN', 'HUMAN');--> statement-breakpoint
CREATE TYPE "public"."scheduled_action_kind" AS ENUM('deadline', 'approval_timeout', 'human_check', 'lost_poke_check', 'missed_expiry', 'permission_request_cooldown');--> statement-breakpoint
CREATE TYPE "public"."task_event_type" AS ENUM('created', 'edited', 'plan_generated', 'poke_fired', 'poke_lost', 'snoozed', 'done', 'photo_sent', 'approved', 're_requested', 'dropped', 'missed', 'cancelled', 'human_notified', 'permission_missing');--> statement-breakpoint
CREATE TYPE "public"."task_status" AS ENUM('SCHEDULED', 'AWAITING_APPROVAL', 'REJECTED', 'DONE', 'DROPPED', 'MISSED', 'CANCELLED');--> statement-breakpoint
CREATE TABLE "devices" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"person_id" uuid NOT NULL,
	"fcm_token" text NOT NULL,
	"platform" "device_platform" NOT NULL,
	"app_version" text NOT NULL,
	"permissions" jsonb DEFAULT '{}'::jsonb NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "devices_fcm_token_unique" UNIQUE("fcm_token")
);
--> statement-breakpoint
CREATE TABLE "edges" (
	"from_person_id" uuid NOT NULL,
	"to_person_id" uuid NOT NULL,
	"type" "edge_type" NOT NULL,
	"status" "edge_status" NOT NULL,
	"weights" jsonb DEFAULT '{}'::jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "edges_from_person_id_to_person_id_pk" PRIMARY KEY("from_person_id","to_person_id")
);
--> statement-breakpoint
CREATE TABLE "idempotency_keys" (
	"key" uuid NOT NULL,
	"caller_id" uuid NOT NULL,
	"command" text NOT NULL,
	"result" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "idempotency_keys_caller_id_key_pk" PRIMARY KEY("caller_id","key")
);
--> statement-breakpoint
CREATE TABLE "invites" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"inviter_id" uuid NOT NULL,
	"invitee_phone" text NOT NULL,
	"relationship" "edge_type" NOT NULL,
	"prefilled_name" text NOT NULL,
	"code" text NOT NULL,
	"status" "invite_status" DEFAULT 'open' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "invites_code_unique" UNIQUE("code")
);
--> statement-breakpoint
CREATE TABLE "people" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"phone" text NOT NULL,
	"display_name" text NOT NULL,
	"quiet_hours_start" time DEFAULT '23:00' NOT NULL,
	"quiet_hours_end" time DEFAULT '07:00' NOT NULL,
	"time_zone" text DEFAULT 'Europe/Istanbul' NOT NULL,
	"use_connections_for_my_tasks" boolean DEFAULT true NOT NULL,
	"notify_me_for_others_tasks" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deleted_at" timestamp with time zone,
	CONSTRAINT "people_phone_unique" UNIQUE("phone")
);
--> statement-breakpoint
CREATE TABLE "poke_steps" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"task_id" uuid NOT NULL,
	"plan_version" bigint NOT NULL,
	"seq" bigint NOT NULL,
	"planned_at" timestamp with time zone NOT NULL,
	"level" "poke_level" NOT NULL,
	"fired_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "scheduled_actions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"kind" "scheduled_action_kind" NOT NULL,
	"task_id" uuid,
	"due_at" timestamp with time zone NOT NULL,
	"payload" jsonb DEFAULT '{}'::jsonb NOT NULL,
	"done_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "task_events" (
	"id" bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "task_events_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1),
	"task_id" uuid NOT NULL,
	"actor_id" uuid,
	"type" "task_event_type" NOT NULL,
	"payload" jsonb DEFAULT '{}'::jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "tasks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"sender_id" uuid NOT NULL,
	"recipient_id" uuid NOT NULL,
	"title" text NOT NULL,
	"detail" text,
	"deadline" timestamp with time zone NOT NULL,
	"finish_mode" "finish_mode" NOT NULL,
	"raw_text" text,
	"status" "task_status" DEFAULT 'SCHEDULED' NOT NULL,
	"late" boolean DEFAULT false NOT NULL,
	"plan_version" bigint DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "devices" ADD CONSTRAINT "devices_person_id_people_id_fk" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "edges" ADD CONSTRAINT "edges_from_person_id_people_id_fk" FOREIGN KEY ("from_person_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "edges" ADD CONSTRAINT "edges_to_person_id_people_id_fk" FOREIGN KEY ("to_person_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "idempotency_keys" ADD CONSTRAINT "idempotency_keys_caller_id_people_id_fk" FOREIGN KEY ("caller_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "invites" ADD CONSTRAINT "invites_inviter_id_people_id_fk" FOREIGN KEY ("inviter_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "poke_steps" ADD CONSTRAINT "poke_steps_task_id_tasks_id_fk" FOREIGN KEY ("task_id") REFERENCES "public"."tasks"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "scheduled_actions" ADD CONSTRAINT "scheduled_actions_task_id_tasks_id_fk" FOREIGN KEY ("task_id") REFERENCES "public"."tasks"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "task_events" ADD CONSTRAINT "task_events_task_id_tasks_id_fk" FOREIGN KEY ("task_id") REFERENCES "public"."tasks"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "task_events" ADD CONSTRAINT "task_events_actor_id_people_id_fk" FOREIGN KEY ("actor_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "tasks" ADD CONSTRAINT "tasks_sender_id_people_id_fk" FOREIGN KEY ("sender_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "tasks" ADD CONSTRAINT "tasks_recipient_id_people_id_fk" FOREIGN KEY ("recipient_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "devices_person_idx" ON "devices" USING btree ("person_id");--> statement-breakpoint
CREATE INDEX "edges_to_person_idx" ON "edges" USING btree ("to_person_id");--> statement-breakpoint
CREATE INDEX "invites_inviter_idx" ON "invites" USING btree ("inviter_id");--> statement-breakpoint
CREATE INDEX "invites_invitee_phone_idx" ON "invites" USING btree ("invitee_phone");--> statement-breakpoint
CREATE UNIQUE INDEX "poke_steps_task_plan_seq_idx" ON "poke_steps" USING btree ("task_id","plan_version","seq");--> statement-breakpoint
CREATE INDEX "scheduled_actions_due_idx" ON "scheduled_actions" USING btree ("due_at") WHERE "scheduled_actions"."done_at" is null;--> statement-breakpoint
CREATE INDEX "task_events_task_idx" ON "task_events" USING btree ("task_id");--> statement-breakpoint
CREATE INDEX "tasks_sender_idx" ON "tasks" USING btree ("sender_id");--> statement-breakpoint
CREATE INDEX "tasks_recipient_idx" ON "tasks" USING btree ("recipient_id");