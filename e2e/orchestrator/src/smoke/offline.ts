// Smoke 10 (docs/testing.md, Smoke set), with the slice 8 screens: Eren loses his connection, home shows the banner
// (6a) and a text he writes waits on the phone as "Sırada"; once the connection is back it goes out by itself and
// the task is on home. Runs after smoke 9: Eren is signed in on his device.
import type { RunContext } from "../context";
import { devices } from "../devices";
import * as flows from "../flows";

/** A confident task to self in the run's extraction fixtures (smoke 1). */
const TASK_TEXT = "para cek";

export async function offline(run: RunContext): Promise<void> {
  const { eren } = devices;

  await run.step("eren-offline-write", async () => {
    await eren.wakeAndUnlock();
    await eren.offline();
    try {
      await flows.offlineWrite(eren, TASK_TEXT);
    } catch (error) {
      // The emulator keeps airplane mode across runs: give the network back before failing.
      await eren.online();
      throw error;
    }
  });

  await run.step("eren-back-online", async () => {
    await eren.online();
    await flows.offlineSent(eren, "Para çek");
  });
}
