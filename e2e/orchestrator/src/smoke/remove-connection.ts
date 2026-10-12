// Smoke 8 (docs/testing.md, Smoke set), with the slice 6 screens: Eren opens profile (5a), then Selin (5b), and
// removes the connection. The task Selin gave a new time in smoke 7 is cancelled with reason connection_removed, his
// alarms for it go, and both sides see the connection as removed.
// Runs after smoke 7: that task is SCHEDULED with alarms on Eren's device.
import { Phone, signIn } from "../api";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const EREN_PHONE = "+905320000002";
const SELIN_PHONE = "+905320000001";

export async function removeConnection(run: RunContext): Promise<void> {
  const { eren } = devices;
  const erenApi = new Phone(await signIn(EREN_PHONE));
  const selinApi = new Phone(await signIn(SELIN_PHONE));

  const taskId = await run.step("open-task-before-removal", async () => {
    const task = (await erenApi.tasks()).find((t) => t.status === "SCHEDULED");
    if (!task) throw new Error("smoke 7 left no SCHEDULED task to Eren");
    return task.id;
  });

  await run.step("eren-removes-selin", async (dir) => {
    await eren.wakeAndUnlock();
    await eren.maestro("eren/remove-connection.yaml", dir);
  });

  await run.step("connection-removed", async () => {
    await waitFor(
      "the task to be CANCELLED on the server",
      async () => {
        const task = (await erenApi.tasks()).find((t) => t.id === taskId);
        return task?.status === "CANCELLED" ? task : null;
      },
      30_000,
    );
    for (const [who, api] of [
      ["Eren", erenApi],
      ["Selin", selinApi],
    ] as const) {
      const statuses = (await api.connections()).map((c) => c.status);
      if (statuses.length === 0 || statuses.some((s) => s !== "removed")) {
        throw new Error(`${who} still sees a connection: ${statuses.join(", ")}`);
      }
    }
    await waitFor(
      "no pending poke alarms on Eren's device",
      async () => ((await eren.pendingPokeAlarms()) === 0 ? true : null),
      30_000,
    );
  });
}
