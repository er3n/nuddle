-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- The timer that deletes what is left of a deleted account a week later (NUD-89).
-- The snapshot next to it was generated after relinking three parallel snapshots (repeat_rule,
-- poke_from, notice_finish_mode) into one chain; their columns were already added by those files.
ALTER TYPE "public"."scheduled_action_kind" ADD VALUE 'account_purge' AFTER 'photo_cleanup';
