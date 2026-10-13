-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- "Kaldır" on the recipient's missed card (NUD-49): when they removed it from their list. Nullable, no rewrite.
ALTER TABLE "tasks" ADD COLUMN "removed_by_recipient_at" timestamp with time zone;
