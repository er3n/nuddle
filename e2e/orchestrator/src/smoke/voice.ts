// Smoke 11 (docs/testing.md, Smoke set), with the slice 9 screen: Sibel holds the mic and speaks a task (4h); the
// e2e flavor "hears" the text the orchestrator put on her phone, and lifting the finger writes it like typed text, so
// the task is created with the undo toast. Runs after smoke 10: Sibel is signed in on her device.
import type { RunContext } from "../context";
import { devices } from "../devices";
import * as flows from "../flows";

/** A confident task to self in the run's extraction fixtures (smoke 1). */
const SPOKEN = "para cek";

export async function voice(run: RunContext): Promise<void> {
  const { sibel } = devices;

  await run.step("sibel-speaks-task", async () => {
    await sibel.wakeAndUnlock();
    await sibel.injectSpeech(SPOKEN);
    await flows.voiceTask(sibel);
  });
}
