// Smoke 1 (docs/testing.md, Smoke set): sign up with a test number, write a task to self in the
// compose bar (2a), see it next up on home (2f), the full screen poke rings, "Bitti", the task leaves
// home and is done on the server.
import { writeFileSync } from "node:fs";
import { join } from "node:path";
import postgres from "postgres";
import { Phone, signIn, testApi } from "../api";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const SIBEL_PHONE = "+905320000001";
/** Compressed plans divide offsets by 60 (docs/architecture.md, E2E mode): with a deadline 90 minutes
 * away, the poke window opens in about 30 s and the FULL_SCREEN step rings about 75 s after creation. */
const DEADLINE_MINUTES = 90;
/** What the flow types into the compose bar; the fixture extractor answers it (docs/testing.md). */
const TASK_TEXT = "para cek";

/**
 * The run's extraction fixtures (E2E_EXTRACTOR_FIXTURES): a confident task to self, due
 * DEADLINE_MINUTES after `now`, so writing it creates it directly (undo toast, no preview).
 */
export function smokeExtractions(now: Date): Record<string, unknown> {
  return {
    [TASK_TEXT]: {
      recipient: "self",
      title: "Para çek",
      detail: null,
      deadline: new Date(now.getTime() + DEADLINE_MINUTES * 60_000).toISOString(),
      finishMode: "done",
      missing: [],
    },
  };
}

/** A one hour quiet window starting 12 hours after `now`, in Sibel's time zone (Europe/Istanbul). */
export function quietHoursAwayFrom(now: Date): { start: string; end: string } {
  const hour = Number(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: "Europe/Istanbul",
      hour: "2-digit",
      hourCycle: "h23",
    }).format(now),
  );
  const at = (h: number) => `${String(h % 24).padStart(2, "0")}:00`;
  return { start: at(hour + 12), end: at(hour + 13) };
}

/** Clean server, Sibel signs up on her emulator, her device registers, her quiet hours move away from now. */
export async function signUpSibel(run: RunContext, databaseUrl: string): Promise<void> {
  const sibel = devices.sibel;

  await run.step("reset", async () => {
    await testApi.reset();
  });

  await run.step("sign-up", async (dir) => {
    await sibel.wakeAndUnlock();
    await sibel.maestro("sibel/sign-up.yaml", dir, {
      PHONE: SIBEL_PHONE.slice(3),
      NAME: "Sibel",
      DONE: "home.screen",
    });
  });

  await run.step("device-registered", async (dir) => {
    const db = postgres(databaseUrl, { max: 1, onnotice: () => undefined });
    try {
      const device = await waitFor(
        "Sibel's device registration with a FID",
        async () => {
          const rows = await db<{ fid: string; permissions: unknown }[]>`
            select d.fid, d.permissions from devices d
            join people p on p.id = d.person_id
            where p.phone = ${SIBEL_PHONE} and d.fid <> ''`;
          return rows[0] ?? null;
        },
        60_000,
      );
      writeFileSync(join(dir, "permissions.json"), JSON.stringify(device.permissions, null, 2));
      // The default quiet hours (23:00-07:00) would swallow the whole poke window of a run at night
      // and leave an empty plan (NUD-35). Move them 12 hours away from now; the device gets them with
      // the sync that the new task's hint starts.
      const { start, end } = quietHoursAwayFrom(new Date());
      await db`update people set quiet_hours_start = ${start}, quiet_hours_end = ${end} where phone = ${SIBEL_PHONE}`;
      run.log(`quiet hours ${start}-${end}`);
    } finally {
      await db.end();
    }
  });
}

export async function taskToSelf(run: RunContext, databaseUrl: string): Promise<void> {
  const sibel = devices.sibel;
  await signUpSibel(run, databaseUrl);

  await run.step("write-task", async (dir) => {
    await sibel.maestro("sibel/write-task.yaml", dir, { TEXT: TASK_TEXT });
  });

  const { phone, taskId } = await run.step("task-created", async () => {
    // A second session for Sibel, as another of her phones would sign in, to check the server.
    const phone = new Phone(await signIn(SIBEL_PHONE));
    const task = await waitFor(
      "the written task on the server",
      async () => (await phone.tasks()).find((t) => t.status === "SCHEDULED") ?? null,
      30_000,
    );
    run.log(`task ${task.id}, deadline ${task.deadline}`);
    return { phone, taskId: task.id };
  });

  await run.step("plan-on-device", async () => {
    // The FCM hint makes the app sync and set one alarm per step.
    const alarms = await waitFor(
      "poke alarms on Sibel's device (FCM hint, then sync)",
      async () => {
        const n = await sibel.pendingPokeAlarms();
        return n > 0 ? n : null;
      },
      60_000,
    );
    run.log(`${String(alarms)} poke alarms set`);
    await sibel.screenOff();
  });

  await run.step("full-screen-poke", async (dir) => {
    await sibel.maestro("sibel/wait-poke.yaml", dir);
  });

  await run.step("tap-done", async (dir) => {
    await sibel.maestro("sibel/poke-done.yaml", dir);
  });

  await run.step("home-after-done", async (dir) => {
    await sibel.maestro("sibel/home-done.yaml", dir);
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
      throw new Error(`Sibel still has open tasks: ${open.map((t) => t.id).join(", ")}`);
    await waitFor(
      "no pending poke alarms on Sibel's device",
      async () => ((await sibel.pendingPokeAlarms()) === 0 ? true : null),
      30_000,
    );
  });
}
