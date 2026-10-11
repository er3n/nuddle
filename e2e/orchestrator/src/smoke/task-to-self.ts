// Smoke 1 (docs/testing.md, Smoke set): sign up with a test number, a task to self, the full screen
// poke rings, "Bitti", the task is done. Until slice 1 the task is created through the API, not the
// app (NUD-10 decision), and "the task leaves the list" is checked on the server and the device's
// alarms, because home has no task list yet.
// TODO(slice 1, 2a/2b/2f): write the task in the app and assert the home list instead.
import { writeFileSync } from "node:fs";
import { join } from "node:path";
import postgres from "postgres";
import { Phone, signIn, testApi } from "../api";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const SELIN_PHONE = "+905320000001";
/** Compressed plans divide offsets by 60 (docs/architecture.md, E2E mode): with a deadline 90 minutes
 * away, the poke window opens in about 30 s and the FULL_SCREEN step rings about 75 s after creation. */
const DEADLINE_MINUTES = 90;

export async function taskToSelf(run: RunContext, databaseUrl: string): Promise<void> {
  const selin = devices.selin;

  await run.step("reset", async () => {
    await testApi.reset();
  });

  await run.step("sign-up", async (dir) => {
    await selin.wakeAndUnlock();
    await selin.maestro("selin/sign-up.yaml", dir, { PHONE: SELIN_PHONE.slice(3), NAME: "Selin" });
  });

  await run.step("device-registered", async (dir) => {
    const db = postgres(databaseUrl, { max: 1, onnotice: () => undefined });
    try {
      const device = await waitFor(
        "Selin's device registration with an FCM token",
        async () => {
          const rows = await db<{ fcm_token: string; permissions: unknown }[]>`
            select d.fcm_token, d.permissions from devices d
            join people p on p.id = d.person_id
            where p.phone = ${SELIN_PHONE} and d.fcm_token <> ''`;
          return rows[0] ?? null;
        },
        60_000,
      );
      writeFileSync(join(dir, "permissions.json"), JSON.stringify(device.permissions, null, 2));
    } finally {
      await db.end();
    }
  });

  const { phone, taskId } = await run.step("create-task", async () => {
    // A second session for Selin, as another of her phones would sign in.
    const phone = new Phone(await signIn(SELIN_PHONE));
    const deadline = new Date(Date.now() + DEADLINE_MINUTES * 60_000);
    const id = await phone.createTaskToSelf("Para çek", deadline);
    run.log(`task ${id}, deadline ${deadline.toISOString()}`);
    return { phone, taskId: id };
  });

  await run.step("plan-on-device", async () => {
    // The FCM hint makes the app sync and set one alarm per step.
    const alarms = await waitFor(
      "poke alarms on Selin's device (FCM hint, then sync)",
      async () => {
        const n = await selin.pendingPokeAlarms();
        return n > 0 ? n : null;
      },
      60_000,
    );
    run.log(`${String(alarms)} poke alarms set`);
    await selin.screenOff();
  });

  await run.step("full-screen-poke", async (dir) => {
    await selin.maestro("selin/wait-poke.yaml", dir);
  });

  await run.step("tap-done", async (dir) => {
    await selin.maestro("selin/poke-done.yaml", dir);
  });

  await run.step("task-done", async () => {
    await waitFor(
      "the task to be DONE on the server",
      async () => {
        const task = (await phone.tasks()).find((t) => t.id === taskId);
        return task?.status === "DONE" ? task : null;
      },
      60_000,
    );
    const open = (await phone.tasks()).filter((t) => t.status === "SCHEDULED");
    if (open.length > 0)
      throw new Error(`Selin still has open tasks: ${open.map((t) => t.id).join(", ")}`);
    await waitFor(
      "no pending poke alarms on Selin's device",
      async () => ((await selin.pendingPokeAlarms()) === 0 ? true : null),
      30_000,
    );
  });
}
