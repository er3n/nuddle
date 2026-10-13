-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Devices are reached by Firebase Installation ID instead of registration token (ADR 0015).
-- The stored tokens are not FIDs, so the rows go; every app launch registers the device again.
DELETE FROM "devices";--> statement-breakpoint
-- Only the API reads this table, and it ships together with the rename (no users before the beta).
-- squawk-ignore renaming-column
ALTER TABLE "devices" RENAME COLUMN "fcm_token" TO "fid";--> statement-breakpoint
-- Renaming keeps the unique index, so there is no rebuild. No client refers to the constraint name.
-- squawk-ignore renaming-object
ALTER TABLE "devices" RENAME CONSTRAINT "devices_fcm_token_unique" TO "devices_fid_unique";
