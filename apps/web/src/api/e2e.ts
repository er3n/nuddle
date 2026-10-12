// E2E mode (docs/architecture.md, E2E mode; docs/testing.md, Backend E2E mode). Only imported when
// E2E=1: test endpoints, the shiftable clock and compressed plans. A test asserts that the routes
// return 404 otherwise.
import { Hono } from "hono";
import { sql } from "drizzle-orm";
import { z } from "zod";
import type { Outcome } from "../domain/task-machine";
import type { OffsetClock } from "../infra/clock";
import { inWriteTx, upsertEdges, upsertPerson } from "../infra/db/store";
import type { AppDeps } from "./deps";
import { ApiError } from "./errors";
import type { AppEnv } from "./routes";
import { runTick } from "./services";

/** Steps one minute apart in production ring one second apart in E2E. */
const COMPRESSION = 60;

const compress = (t: Date, now: Date): Date =>
  new Date(now.getTime() + Math.max(0, t.getTime() - now.getTime()) / COMPRESSION);

/** Compress the poke plan and its step timers toward `now`; deadlines stay as written. */
export function compressOutcome(o: Outcome, now: Date): Outcome {
  return {
    ...o,
    plan: {
      ...o.plan,
      steps: o.plan.steps.map((s) => ({ ...s, plannedAt: compress(s.plannedAt, now) })),
    },
    scheduledActions: o.scheduledActions.map((a) =>
      a.kind === "human_check" || a.kind === "lost_poke_check"
        ? { ...a, dueAt: compress(a.dueAt, now) }
        : a,
    ),
  };
}

const SeedBody = z.object({
  people: z.array(
    z.object({
      phone: z.string().regex(/^\+[1-9]\d{7,14}$/),
      displayName: z.string().min(1),
      timeZone: z.string().default("Europe/Istanbul"),
    }),
  ),
  connections: z
    .array(
      z.object({
        from: z.string(),
        to: z.string(),
        type: z.enum(["spouse", "parent", "child", "sibling", "roommate", "other"]),
      }),
    )
    .default([]),
});

export function e2eRoutes(deps: AppDeps, e2e: { clock: OffsetClock }): Hono<AppEnv> {
  const r = new Hono<AppEnv>();

  r.post("/reset", async (c) => {
    await deps.db.execute(
      sql`truncate idempotency_keys, devices, scheduled_actions, poke_steps, task_events, tasks, invites, edges, people`,
    );
    e2e.clock.reset();
    return c.json({ ok: true });
  });

  /** People (as Supabase Auth users, so test numbers sign in as them) and connections. */
  r.post("/seed", async (c) => {
    const parsed = SeedBody.safeParse(await c.req.json());
    if (!parsed.success) throw new ApiError("validation_failed", z.prettifyError(parsed.error));
    const ids = new Map<string, string>();
    for (const p of parsed.data.people) ids.set(p.phone, await deps.auth.ensureUser(p.phone));
    const now = deps.clock.now();
    await inWriteTx(deps.db, async (tx) => {
      for (const p of parsed.data.people) {
        await upsertPerson(tx, { id: ids.get(p.phone) ?? "", ...p, now });
      }
      await upsertEdges(
        tx,
        parsed.data.connections.map((e) => ({
          fromPersonId: ids.get(e.from) ?? "",
          toPersonId: ids.get(e.to) ?? "",
          type: e.type,
          status: "active" as const,
          notifyMe: true,
        })),
        now,
      );
    });
    return c.json({ people: Object.fromEntries(ids) });
  });

  r.post("/tick", async (c) => c.json({ handled: await runTick(deps) }));

  r.post("/advance-clock", async (c) => {
    const parsed = z.object({ ms: z.int().min(0) }).safeParse(await c.req.json());
    if (!parsed.success) throw new ApiError("validation_failed", z.prettifyError(parsed.error));
    e2e.clock.advance(parsed.data.ms);
    return c.json({ now: e2e.clock.now().toISOString() });
  });

  return r;
}
