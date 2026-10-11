import { eq } from "drizzle-orm";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { people, tasks } from "../../src/infra/db/schema";
import { createTestDatabase, type TestDatabase } from "./db";

describe("tasks table", () => {
  let t: TestDatabase;

  beforeAll(async () => {
    t = await createTestDatabase();
  });

  afterAll(async () => {
    await t.drop();
  });

  it("inserts a task from Selin to Eren and reads it back", async () => {
    const [selin, eren] = await t.db
      .insert(people)
      .values([
        { phone: "+905320000001", displayName: "Selin" },
        { phone: "+905320000002", displayName: "Eren" },
      ])
      .returning();
    if (!selin || !eren) throw new Error("seed people not returned");

    const deadline = new Date("2026-10-12T06:00:00Z"); // 09:00 Istanbul
    const [created] = await t.db
      .insert(tasks)
      .values({
        senderId: selin.id,
        recipientId: eren.id,
        title: "Temizlikçi için para çek",
        detail: "1.000 TL",
        deadline,
        finishMode: "photo",
        rawText: "eren yarın 9dan önce temizlikçi için 1000 çeksin",
      })
      .returning({ id: tasks.id });
    if (!created) throw new Error("task not returned");

    const [read] = await t.db.select().from(tasks).where(eq(tasks.id, created.id));
    expect(read).toMatchObject({
      senderId: selin.id,
      recipientId: eren.id,
      title: "Temizlikçi için para çek",
      detail: "1.000 TL",
      deadline,
      finishMode: "photo",
      status: "SCHEDULED",
      late: false,
      planVersion: 0,
    });
  });
});
