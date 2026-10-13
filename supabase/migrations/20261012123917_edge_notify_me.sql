-- Fail fast instead of queueing behind locks on a live database (squawk).
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- "Sibel'in işleri için bana haber verilebilir" (5b): a constant default, no table rewrite.
ALTER TABLE "edges" ADD COLUMN "notify_me" boolean DEFAULT true NOT NULL;
