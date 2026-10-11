// Server timers in Postgres (ADR 0007): claims use FOR UPDATE SKIP LOCKED plus a lease, so
// concurrent ticks never run the same timer and a crashed run is retried.
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import {
  claimDueActions,
  inWriteTx,
  lockOpenAction,
  markActionDone,
} from "../../src/infra/db/store";
import { scheduledActions } from "../../src/infra/db/schema";
import { createTestDatabase, type TestDatabase } from "../support/db";

describe("scheduled_actions claims", () => {
  let t: TestDatabase;
  const now = new Date("2026-10-12T09:00:00+03:00");

  beforeAll(async () => {
    t = await createTestDatabase();
    await t.db.insert(scheduledActions).values(
      Array.from({ length: 40 }, (_, i) => ({
        kind: "permission_request_cooldown" as const,
        taskId: null,
        dueAt: new Date(now.getTime() - i * 1000),
        payload: { i },
      })),
    );
    await t.db.insert(scheduledActions).values({
      kind: "permission_request_cooldown",
      taskId: null,
      dueAt: new Date(now.getTime() + 60_000),
    });
  });
  afterAll(async () => {
    await t.drop();
  });

  it("concurrent claims split the due rows without overlap and skip future ones", async () => {
    const [a, b, c] = await Promise.all([
      claimDueActions(t.db, now, 60_000, 15),
      claimDueActions(t.db, now, 60_000, 15),
      claimDueActions(t.db, now, 60_000, 15),
    ]);
    const ids = [...a, ...b, ...c].map((x) => x.id);
    expect(ids).toHaveLength(40);
    expect(new Set(ids).size).toBe(40);
    expect(await claimDueActions(t.db, now, 60_000, 100)).toEqual([]);
  });

  it("re-claims after the lease runs out unless the timer was finished", async () => {
    const later = new Date(now.getTime() + 61_000);
    const again = await claimDueActions(t.db, later, 60_000, 100);
    // 40 expired leases plus the row that became due.
    expect(again).toHaveLength(41);

    const [first] = again;
    if (!first) throw new Error("nothing claimed");
    await inWriteTx(t.db, (tx) => markActionDone(tx, first.id, later));
    expect(await inWriteTx(t.db, (tx) => lockOpenAction(tx, first.id))).toBe(false);
    const evenLater = new Date(later.getTime() + 61_000);
    expect((await claimDueActions(t.db, evenLater, 60_000, 100)).map((x) => x.id)).not.toContain(
      first.id,
    );
  });
});
