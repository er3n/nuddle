-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- The profile picture's path in the private bucket (NUD-72); null shows the initial.
ALTER TABLE "people" ADD COLUMN "avatar_path" text;