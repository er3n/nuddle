// Smoke 9 (docs/testing.md, Smoke set), with the slice 7 screens: Eren writes a task to himself and his app is force
// stopped, as a phone brand's battery manager does, before its pokes ring. The alarms are gone; on the next launch
// the bell says "Son dürtmen çalmadı" (2g) and "Düzelt" opens the brand steps (2h), the default one on the emulator.
// Runs after smoke 8: Eren is signed in on his device, with quiet hours away from now (smoke 4).
import { Phone, signIn } from "../api";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const EREN_PHONE = "+905320000002";
/** As in smoke 1: compressed, the first poke is due about 30 s after the task is written and the second 30 s later. */
const DEADLINE_MINUTES = 90;
/** Long enough after the force stop for the first two pokes to come due. */
const WAIT_FOR_POKES_MS = 70_000;

export async function missedRing(run: RunContext): Promise<void> {
  const { eren } = devices;
  const erenApi = new Phone(await signIn(EREN_PHONE));

  await run.step("eren-task-to-self", async () => {
    await eren.wakeAndUnlock();
    const taskId = await erenApi.createTaskToSelf(
      "Ekmek al",
      new Date(Date.now() + DEADLINE_MINUTES * 60_000),
    );
    run.log(`task ${taskId}`);
    const alarms = await waitFor(
      "poke alarms on Eren's device (FCM hint, then sync)",
      async () => {
        const n = await eren.pendingPokeAlarms();
        return n > 0 ? n : null;
      },
      30_000,
    );
    run.log(`${String(alarms)} poke alarms set`);
  });

  await run.step("force-stop", async () => {
    const stoppedAt = Date.now();
    await eren.forceStop();
    if ((await eren.pendingPokeAlarms()) !== 0)
      throw new Error("a force stop should take the app's alarms");
    await new Promise((resolve) =>
      setTimeout(resolve, Math.max(0, stoppedAt + WAIT_FOR_POKES_MS - Date.now())),
    );
  });

  await run.step("eren-sees-missed-ring", async (dir) => {
    await eren.wakeAndUnlock();
    await eren.maestro("eren/missed-ring.yaml", dir);
  });
}
