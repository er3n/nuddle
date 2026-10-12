// Smoke 7 (docs/testing.md, Smoke set), with the slice 5 screens: Selin writes a task to Eren, the full screen poke
// rings, Eren answers "Bugün yapamayacağım" with a ready note (7f), his pokes stop; Selin sees it dropped with the
// note (5d) and gives a new time, and Eren's phone has alarms again.
// Runs after smoke 3: Selin and Eren are connected and signed in, with no open task between them.
import { Phone, signIn } from "../api";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const EREN_PHONE = "+905320000002";
/** As in smoke 1: the FULL_SCREEN step of a compressed plan rings about 75 s after creation. */
const DEADLINE_MINUTES = 90;
/** What Selin types; the fixture extractor answers it (docs/testing.md). */
const TASK_TEXT = "eren kuru temizlemeyi alsin";

/** A confident task to Eren, so writing it sends it directly. */
export function dropTaskExtractions(now: Date): Record<string, unknown> {
  return {
    [TASK_TEXT]: {
      recipient: "Eren",
      title: "Kuru temizlemeyi al",
      detail: null,
      deadline: new Date(now.getTime() + DEADLINE_MINUTES * 60_000).toISOString(),
      finishMode: "done",
      missing: [],
    },
  };
}

export async function dropTask(run: RunContext): Promise<void> {
  const { selin, eren } = devices;

  await run.step("write-drop-task", async (dir) => {
    await selin.wakeAndUnlock();
    await selin.maestro("selin/write-task-to-other.yaml", dir, { TEXT: TASK_TEXT });
  });

  const { phone, taskId } = await run.step("drop-task-created", async () => {
    const phone = new Phone(await signIn(EREN_PHONE));
    const task = await waitFor(
      "the task to Eren on the server",
      async () => (await phone.tasks()).find((t) => t.status === "SCHEDULED") ?? null,
      30_000,
    );
    run.log(`task ${task.id}, deadline ${task.deadline}`);
    return { phone, taskId: task.id };
  });

  await run.step("eren-drop-plan", async () => {
    await waitFor(
      "poke alarms on Eren's device",
      async () => ((await eren.pendingPokeAlarms()) > 0 ? true : null),
      60_000,
    );
    await eren.screenOff();
  });

  await run.step("eren-drop-poke", async (dir) => {
    await eren.maestro("selin/wait-poke.yaml", dir);
  });

  await run.step("eren-cant-today", async (dir) => {
    await eren.maestro("eren/poke-cant-today.yaml", dir);
  });

  await run.step("dropped", async () => {
    await waitFor(
      "the task to be DROPPED on the server",
      async () => {
        const task = (await phone.tasks()).find((t) => t.id === taskId);
        return task?.status === "DROPPED" ? task : null;
      },
      60_000,
    );
    await waitFor(
      "no pending poke alarms on Eren's device",
      async () => ((await eren.pendingPokeAlarms()) === 0 ? true : null),
      30_000,
    );
  });

  await run.step("selin-new-time", async (dir) => {
    await selin.wakeAndUnlock();
    await selin.maestro("selin/new-time.yaml", dir);
  });

  await run.step("rescheduled", async () => {
    await waitFor(
      "the task to be SCHEDULED again on the server",
      async () => {
        const task = (await phone.tasks()).find((t) => t.id === taskId);
        return task?.status === "SCHEDULED" ? task : null;
      },
      60_000,
    );
    await waitFor(
      "poke alarms on Eren's device for the new time",
      async () => ((await eren.pendingPokeAlarms()) > 0 ? true : null),
      60_000,
    );
  });
}
