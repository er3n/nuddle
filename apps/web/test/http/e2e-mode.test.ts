// E2E mode (docs/architecture.md, E2E mode): the test endpoints exist only with E2E=1. Uses the
// production wiring so the switch itself is under test.
import { afterAll, beforeAll, describe, expect, inject, it } from "vitest";
import { createProductionApp } from "../../src/api/production";
import { createDatabaseFromTemplate, type TestDatabase } from "../support/db";

const testRoutes = [
  "/api/test/reset",
  "/api/test/reset-tasks",
  "/api/test/seed",
  "/api/test/tick",
  "/api/test/advance-clock",
];

describe("http: E2E mode", () => {
  let database: TestDatabase;
  let env: Record<string, string>;

  beforeAll(async () => {
    database = await createDatabaseFromTemplate(inject("templateDatabase"));
    const [row] = await database.sql<{ name: string }[]>`select current_database() as name`;
    const url = new URL("postgresql://postgres:postgres@127.0.0.1:54322/");
    url.pathname = `/${row?.name ?? ""}`;
    // Nothing here is contacted while the routes under test run.
    env = {
      DATABASE_URL: url.toString(),
      SUPABASE_URL: "http://127.0.0.1:54321",
      SUPABASE_SECRET_KEY: "unused",
      TICK_SECRET: "x".repeat(32),
      SEND_SMS_HOOK_SECRET: `v1,whsec_${Buffer.from("unused").toString("base64")}`,
      OPENROUTER_API_KEY: "unused",
      OPENROUTER_MODEL: "unused",
      E2E_EXTRACTOR_FIXTURES: new URL("../../../../e2e/fixtures/extractions.json", import.meta.url)
        .pathname,
    };
  });
  afterAll(async () => {
    await database.drop();
  });

  it("returns 404 for every test endpoint when E2E is not set", async () => {
    const app = await createProductionApp(env);
    for (const path of testRoutes) {
      const res = await app.request(path, { method: "POST", body: "{}" });
      expect(res.status, path).toBe(404);
    }
  });

  it("with E2E=1: the fixture extractor loads, the clock shifts, reset and tick run", async () => {
    const app = await createProductionApp({ ...env, E2E: "1" });
    const post = (path: string, body: object = {}) =>
      app.request(path, {
        method: "POST",
        body: JSON.stringify(body),
        headers: { "content-type": "application/json" },
      });

    expect((await post("/api/test/reset")).status).toBe(200);
    const t0 = new Date(
      ((await (await post("/api/test/advance-clock", { ms: 0 })).json()) as { now: string }).now,
    );
    const t1 = new Date(
      ((await (await post("/api/test/advance-clock", { ms: 3_600_000 })).json()) as { now: string })
        .now,
    );
    expect(t1.getTime() - t0.getTime()).toBeGreaterThanOrEqual(3_600_000);
    expect(await (await post("/api/test/tick")).json()).toEqual({ handled: 0 });
    expect((await post("/api/test/advance-clock", { ms: -1 })).status).toBe(400);
  });

  it("reset-tasks empties the tasks and puts the clock back, people and connections stay (NUD-65)", async () => {
    const app = await createProductionApp({ ...env, E2E: "1" });
    const post = (path: string, body: object = {}) =>
      app.request(path, {
        method: "POST",
        body: JSON.stringify(body),
        headers: { "content-type": "application/json" },
      });
    const [person] = await database.sql<{ id: string }[]>`
      insert into people (id, phone, display_name, time_zone)
      values (gen_random_uuid(), '+905320000009', 'Sibel', 'Europe/Istanbul') returning id`;
    await database.sql`
      insert into tasks (sender_id, recipient_id, title, deadline, finish_mode)
      values (${person?.id ?? ""}, ${person?.id ?? ""}, 'Ekmek al', now() + interval '1 hour', 'done')`;
    const real = Date.now();
    await post("/api/test/advance-clock", { ms: 3_600_000 });

    expect((await post("/api/test/reset-tasks")).status).toBe(200);
    expect(await database.sql`select id from tasks`).toHaveLength(0);
    expect(await database.sql`select id from people where phone = '+905320000009'`).toHaveLength(1);
    const now = new Date(
      ((await (await post("/api/test/advance-clock", { ms: 0 })).json()) as { now: string }).now,
    );
    expect(now.getTime() - real).toBeLessThan(60_000);
  });
});
