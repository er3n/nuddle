// A moment inside the recipient's quiet hours (NUD-120, open-questions#47): writing it asks first,
// and the sender picks the end of quiet hours or the asked time.
import { v7 as uuidv7 } from "uuid";
import { describe, expect, it } from "vitest";
import { at, hhmm, sibelAndEren, TEXT } from "./fixtures";
import type { Client, World } from "./harness";

/** The preview's Kur with the chosen moment (create_task). */
async function createAt(
  world: World,
  sender: Client,
  recipient: Client,
  moment: Date,
): Promise<string> {
  const r = await sender.api.POST("/api/v1/commands/create_task", {
    body: {
      idempotencyKey: uuidv7(),
      recipientId: recipient.uuid,
      title: "Uyan",
      detail: null,
      deadline: moment.toISOString(),
      timing: "at",
      missAfterMinutes: 15,
      finishMode: "done",
      rawText: TEXT.earlyWakeSelf,
    },
  });
  if (!r.data) throw new Error(`create_task failed: ${JSON.stringify(r.error)}`);
  await world.refresh();
  return r.data.taskId;
}

describe("scenarios: a moment inside quiet hours", () => {
  it("a 05:00 wake-up is not created; the sender is offered the end of quiet hours", async () => {
    const { world, eren } = await sibelAndEren();
    await eren.updateSettings({ quietHours: { start: "23:00", end: "06:00" } });

    expect(await eren.write(TEXT.earlyWakeSelf)).toEqual({
      kind: "ask",
      question: "quiet_hours",
      quiet: { start: "23:00", end: "06:00", suggested: at("2026-10-12T06:00:00").toISOString() },
    });
    expect(world.tasksWrittenBy("eren")).toEqual([]);

    const moved = await createAt(world, eren, eren, at("2026-10-12T06:00:00"));
    expect(hhmm(world.task(moved).remindAt ?? world.task(moved).deadline)).toBe("06:00");
  });

  it("Yine de keeps the asked time", async () => {
    const { world, eren } = await sibelAndEren();
    const task = await createAt(world, eren, eren, at("2026-10-12T05:00:00"));
    expect(world.task(task).remindAt).toEqual(at("2026-10-12T05:00:00"));
    expect(world.task(task).status).toBe("SCHEDULED");
  });

  it("for someone else it is their quiet hours that count", async () => {
    const { sibel, eren } = await sibelAndEren();
    await eren.updateSettings({ quietHours: { start: "23:00", end: "06:00" } });
    const r = await sibel.write(TEXT.earlyWakeEren);
    expect(r).toMatchObject({ kind: "ask", question: "quiet_hours", quiet: { end: "06:00" } });
  });
});
