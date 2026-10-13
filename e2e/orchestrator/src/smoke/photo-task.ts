// Smoke 3 (docs/testing.md, Smoke set): Sibel writes a photo task to Eren, Eren gets poked, Bitti on the full
// screen poke opens the camera (4d), he sends a photo, his pokes stop, Sibel approves it (4f), the task is done.
// Runs after smoke 4: Sibel and Eren are connected and signed in, with no open task between them.
import { join } from "node:path";
import { Phone, signIn } from "../api";
import { repoRoot, type RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const EREN_PHONE = "+905320000002";
/** As in smoke 1: the FULL_SCREEN step of a compressed plan rings about 75 s after creation. */
const DEADLINE_MINUTES = 90;
/** What Sibel types; the fixture extractor answers it (docs/testing.md). */
const TASK_TEXT = "eren para ceksin";

/** A confident money task to Eren: finish mode photo, so writing it sends it directly. */
export function photoTaskExtractions(now: Date): Record<string, unknown> {
  return {
    [TASK_TEXT]: {
      recipient: "Eren",
      title: "Para çek",
      detail: "1.000 TL",
      deadline: new Date(now.getTime() + DEADLINE_MINUTES * 60_000).toISOString(),
      finishMode: "photo",
      missing: [],
    },
  };
}

export async function photoTask(run: RunContext): Promise<void> {
  const { sibel, eren } = devices;

  await run.step("inject-photo", async () => {
    await eren.injectPhoto(join(repoRoot, "e2e", "fixtures", "photo.jpg"));
  });

  await run.step("write-photo-task", async (dir) => {
    await sibel.wakeAndUnlock();
    await sibel.maestro("sibel/write-task-to-other.yaml", dir, { TEXT: TASK_TEXT });
  });

  const { phone, taskId } = await run.step("photo-task-created", async () => {
    const phone = new Phone(await signIn(EREN_PHONE));
    const task = await waitFor(
      "the photo task to Eren on the server",
      async () =>
        (await phone.tasks()).find((t) => t.status === "SCHEDULED" && t.finishMode === "photo") ??
        null,
      30_000,
    );
    run.log(`task ${task.id}, deadline ${task.deadline}`);
    return { phone, taskId: task.id };
  });

  await run.step("eren-plan", async () => {
    await waitFor(
      "poke alarms on Eren's device",
      async () => ((await eren.pendingPokeAlarms()) > 0 ? true : null),
      60_000,
    );
    await eren.screenOff();
  });

  await run.step("eren-full-screen-poke", async (dir) => {
    await eren.maestro("sibel/wait-poke.yaml", dir);
  });

  await run.step("eren-bitti-opens-camera", async (dir) => {
    await eren.maestro("sibel/poke-done.yaml", dir);
  });

  await run.step("eren-sends-photo", async (dir) => {
    await eren.wakeAndUnlock();
    await eren.maestro("eren/photo-send.yaml", dir);
  });

  await run.step("awaiting-approval", async () => {
    await waitFor(
      "the task to await approval on the server (upload, then photo_sent)",
      async () => {
        const task = (await phone.tasks()).find((t) => t.id === taskId);
        return task?.status === "AWAITING_APPROVAL" ? task : null;
      },
      90_000,
    );
    await waitFor(
      "no pending poke alarms on Eren's device",
      async () => ((await eren.pendingPokeAlarms()) === 0 ? true : null),
      30_000,
    );
  });

  await run.step("sibel-approves", async (dir) => {
    await sibel.wakeAndUnlock();
    await sibel.maestro("sibel/approve.yaml", dir);
  });

  await run.step("photo-task-done", async () => {
    await waitFor(
      "the task to be DONE on the server",
      async () => {
        const task = (await phone.tasks()).find((t) => t.id === taskId);
        return task?.status === "DONE" ? task : null;
      },
      60_000,
    );
  });
}
