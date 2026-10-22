-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Whom the recipient calls or messages from the poke ("annemi ara", NUD-128).
CREATE TYPE "public"."contact_action" AS ENUM('call', 'message');--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "contact_person_id" uuid;--> statement-breakpoint
ALTER TABLE "tasks" ADD COLUMN "contact_action" "contact_action";--> statement-breakpoint
-- NOT VALID: new and changed rows are checked; the existing rows are all null, so there is nothing to
-- validate (squawk: validating in the same transaction would block reads).
ALTER TABLE "tasks" ADD CONSTRAINT "tasks_contact_person_id_people_id_fk" FOREIGN KEY ("contact_person_id") REFERENCES "public"."people"("id") ON DELETE no action ON UPDATE no action NOT VALID;
