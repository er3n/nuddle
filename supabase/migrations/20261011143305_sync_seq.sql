-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Sync cursor (ADR 0006). The volatile default rewrites each table; that is safe here because no
-- production database exists before stage 7 and the local tables hold only seed rows. Do not copy
-- this pattern for a populated table.
CREATE SEQUENCE "public"."sync_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1;--> statement-breakpoint
-- squawk-ignore adding-field-with-default
ALTER TABLE "edges" ADD COLUMN "change_seq" bigint DEFAULT nextval('sync_seq') NOT NULL;--> statement-breakpoint
-- squawk-ignore adding-field-with-default
ALTER TABLE "invites" ADD COLUMN "change_seq" bigint DEFAULT nextval('sync_seq') NOT NULL;--> statement-breakpoint
ALTER TABLE "scheduled_actions" ADD COLUMN "claimed_until" timestamp with time zone;--> statement-breakpoint
-- squawk-ignore adding-field-with-default
ALTER TABLE "task_events" ADD COLUMN "change_seq" bigint DEFAULT nextval('sync_seq') NOT NULL;--> statement-breakpoint
-- squawk-ignore adding-field-with-default
ALTER TABLE "tasks" ADD COLUMN "change_seq" bigint DEFAULT nextval('sync_seq') NOT NULL;