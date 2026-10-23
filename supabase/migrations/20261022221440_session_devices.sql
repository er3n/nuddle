-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Which device holds each Supabase session, and devices named by someone else (ADR 0027, NUD-142).
-- Ids, the app version and times only. Only the API reads them: RLS on, no grants.
CREATE TYPE "public"."foreign_device_via" AS ENUM('verify', 'refresh', 'sync', 'report_fired', 'log_out');--> statement-breakpoint
CREATE TYPE "public"."session_seen_via" AS ENUM('verify', 'refresh', 'device', 'sync');--> statement-breakpoint
CREATE TABLE "foreign_device_uses" (
	"id" bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "foreign_device_uses_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1),
	"person_id" uuid NOT NULL,
	"session_id" uuid,
	"device_id" uuid NOT NULL,
	"owner_id" uuid,
	"via" "foreign_device_via" NOT NULL,
	"at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE TABLE "session_devices" (
	"session_id" uuid PRIMARY KEY NOT NULL,
	"person_id" uuid NOT NULL,
	"device_id" uuid,
	"app_version" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_seen_via" "session_seen_via" NOT NULL,
	"revoked_at" timestamp with time zone
);
--> statement-breakpoint
ALTER TABLE "devices" ADD COLUMN "model" text;--> statement-breakpoint
CREATE INDEX "foreign_device_uses_person_idx" ON "foreign_device_uses" USING btree ("person_id");--> statement-breakpoint
CREATE INDEX "session_devices_person_idx" ON "session_devices" USING btree ("person_id");--> statement-breakpoint
alter table session_devices enable row level security;--> statement-breakpoint
alter table foreign_device_uses enable row level security;
