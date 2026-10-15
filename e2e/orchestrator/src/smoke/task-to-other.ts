// Smoke 4 (docs/testing.md, Smoke set), with the slice 3 screens: Sibel writes a task to Eren in the
// compose bar (4i), it is Eren's next-up card (4c) and his phone sets its poke alarms; Sibel opens it
// (7c) and cancels it with a reason (7d), and Eren's pending alarms are gone.
// Runs after smoke 2: Sibel and Eren are connected and signed in.
import postgres from "postgres";
import { Phone, signIn } from "../api";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const EREN_PHONE = "+905320000002";
const SIBEL_PHONE = "+905320000001";
/** As in smoke 1: a compressed plan opens its poke window about 30 s after creation. */
const DEADLINE_MINUTES = 90;
/** What Sibel types; the fixture extractor answers it (docs/testing.md). */
const TASK_TEXT = "eren ekmek alsin";

/** A confident task to Eren, so writing it sends it directly (undo toast, no preview). */
export function taskToOtherExtractions(now: Date): Record<string, unknown> {
  return {
    [TASK_TEXT]: {
      recipient: "Eren",
      title: "Ekmek al",
      detail: null,
      deadline: new Date(now.getTime() + DEADLINE_MINUTES * 60_000).toISOString(),
      finishMode: "done",
      missing: [],
    },
  };
}

/** As for Sibel in smoke 1: default quiet hours would swallow the poke window of a run at night. */
export async function erenQuietHours(run: RunContext, databaseUrl: string): Promise<void> {
  await run.step("eren-quiet-hours", async () => {
    const db = postgres(databaseUrl, { max: 1, onnotice: () => undefined });
    try {
      await db`update people p set quiet_hours_start = s.quiet_hours_start, quiet_hours_end = s.quiet_hours_end
               from people s where s.phone = ${SIBEL_PHONE} and p.phone = ${EREN_PHONE}`;
    } finally {
      await db.end();
    }
  });
}

export async function taskToOther(run: RunContext, databaseUrl: string): Promise<void> {
  const { sibel, eren } = devices;

  await erenQuietHours(run, databaseUrl);

  await run.step("write-task-to-eren", async (dir) => {
    await sibel.wakeAndUnlock();
    await sibel.maestro("sibel/write-task-to-other.yaml", dir, { TEXT: TASK_TEXT });
  });

  const { phone, taskId } = await run.step("task-created", async () => {
    const phone = new Phone(await signIn(EREN_PHONE));
    const task = await waitFor(
      "the task to Eren on the server",
      async () => (await phone.tasks()).find((t) => t.status === "SCHEDULED") ?? null,
      30_000,
    );
    run.log(`task ${task.id}, deadline ${task.deadline}`);
    return { phone, taskId: task.id };
  });

  await run.step("eren-home", async (dir) => {
    await eren.wakeAndUnlock();
    await eren.maestro("eren/home-from-sibel.yaml", dir);
  });

  await run.step("eren-alarms", async () => {
    const alarms = await waitFor(
      "poke alarms on Eren's device",
      async () => {
        const n = await eren.pendingPokeAlarms();
        return n > 0 ? n : null;
      },
      60_000,
    );
    run.log(`${String(alarms)} poke alarms set`);
  });

  await run.step("sibel-cancels", async (dir) => {
    await sibel.maestro("sibel/cancel-task.yaml", dir);
  });

  await run.step("cancelled", async () => {
    await waitFor(
      "the task to be CANCELLED on the server",
      async () => {
        const task = (await phone.tasks()).find((t) => t.id === taskId);
        return task?.status === "CANCELLED" ? task : null;
      },
      60_000,
    );
    await waitFor(
      "no pending poke alarms on Eren's device",
      async () => ((await eren.pendingPokeAlarms()) === 0 ? true : null),
      60_000,
    );
  });
}
