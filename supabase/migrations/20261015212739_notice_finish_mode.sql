-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- A one-time message as a finish mode (NUD-79).
ALTER TYPE "public"."finish_mode" ADD VALUE 'notice' AFTER 'photo';