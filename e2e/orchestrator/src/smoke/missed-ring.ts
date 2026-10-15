// Smoke 9 (docs/testing.md, Smoke set), with the slice 7 screens: Eren writes a task to himself and his app is force
// stopped, as a phone brand's battery manager does, before its pokes ring. The alarms are gone; on the next launch
// the bell says "Son dürtmen çalmadı" (2g) and "Düzelt" opens the brand steps (2h), the default one on the emulator.
// Runs after smoke 8: Eren is signed in on his device, with quiet hours away from now (smoke 4).
import { Phone, signIn } from "../api";
import type { RunContext } from "../context";
import type { FakeTime } from "../sync/time";
import { devices } from "../devices";
import * as flows from "../flows";
import { waitFor } from "../proc";

const EREN_PHONE = "+905320000002";
/** As in smoke 1. */
const DEADLINE_MINUTES = 90;
/**
 * Fake time moves to this long before the first poke; the force stop follows at once and the poke comes due in real
 * time while the app is stopped. The force stop's time is the phone's real one, so time cannot move while stopped.
 */
const BEFORE_POKE_MS = 6_000;
/** Past the app's 2 minute grace for a late step (domain Poke.kt, LATE_GRACE). */
const PAST_GRACE_MS = 150_000;

export async function missedRing(run: RunContext, time: FakeTime): Promise<void> {
  const { eren } = devices;
  const erenApi = new Phone(await signIn(EREN_PHONE));

  const taskId = await run.step("eren-task-to-self", async () => {
    await eren.wakeAndUnlock();
    const taskId = await erenApi.createTaskToSelf(
      "Ekmek al",
      new Date((await time.now()).getTime() + DEADLINE_MINUTES * 60_000),
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
    return taskId;
  });

  await run.step("force-stop", async () => {
    await time.toJustBefore(taskId, BEFORE_POKE_MS);
    const stoppedAt = Date.now();
    await eren.forceStop();
    if ((await eren.pendingPokeAlarms()) !== 0)
      throw new Error("a force stop should take the app's alarms");
    await new Promise((resolve) =>
      setTimeout(resolve, Math.max(0, stoppedAt + BEFORE_POKE_MS + 3_000 - Date.now())),
    );
    // On launch the app rings again what is under 2 minutes late, and a ring clears the warning: time moves past every
    // step by more than that, without starting the app.
    await time.pastLastStep(taskId, PAST_GRACE_MS, eren);
  });

  await run.step("eren-sees-missed-ring", async () => {
    await eren.wakeAndUnlock();
    await flows.missedRing(eren);
  });
}
