-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Minutes before an at task's moment the person must start (NUD-70). Default 0, no rewrite.
ALTER TABLE "tasks" ADD COLUMN "lead_minutes" bigint DEFAULT 0 NOT NULL;