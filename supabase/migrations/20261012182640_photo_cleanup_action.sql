-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- A timer that deletes a closed task's photos again until Storage answers (NUD-25).
ALTER TYPE "public"."scheduled_action_kind" ADD VALUE 'photo_cleanup' AFTER 'permission_request_cooldown';
