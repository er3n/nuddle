// The two-person set (`mise run e2e:sync`, NUD-64): one case per thing that happens between a sender and a
// recipient, each checked on both phones (home, alarms, notifications) and on the server. A step the person takes
// is done in the app; the other side only watches its own phone, as Sibel and Eren would.
// Runs after sign-up and invite: Sibel and Eren are connected, signed in, with quiet hours away from now.
// Every case ends with no open task between them, so "home.sent" or "home.nextUp" on screen is the case's task.
import { Phone, signIn, testApi, type SyncTask } from "../api";
import type { RunContext } from "../context";
import { type Device, devices } from "../devices";
import { waitFor } from "../proc";

const SIBEL_PHONE = "+905320000001";
const EREN_PHONE = "+905320000002";
/** Compressed plans (docs/testing.md, Backend E2E mode): the first poke of this deadline rings after minutes. */
const QUIET_DEADLINE_MINUTES = 600;
/** As in smoke 1: the first poke rings about 30 s after creation, the full screen one about 75 s. */
const POKE_DEADLINE_MINUTES = 90;
/** What Sibel types in case 1; the fixture extractor answers it. */
const WRITTEN_TEXT = "eren copu atsin";
const MINUTE = 60_000;

export function twoPersonExtractions(now: Date): Record<string, unknown> {
  return {
    [WRITTEN_TEXT]: {
      recipient: "Eren",
      title: "Çöpü at",
      detail: null,
      deadline: new Date(now.getTime() + QUIET_DEADLINE_MINUTES * MINUTE).toISOString(),
      finishMode: "done",
      missing: [],
    },
  };
}

interface Expect {
  visible?: string[];
  hidden?: string[];
}

/** Waits until home is on [device]'s screen with every [visible] tag and none of the [hidden] ones. */
async function expectHome(device: Device, what: string, expect: Expect, timeoutMs = 60_000) {
  const visible = ["home.screen", ...(expect.visible ?? [])];
  const hidden = expect.hidden ?? [];
  let last = "";
  try {
    await waitFor(
      `${device.role}: ${what}`,
      async () => {
        const tags = await device.screenTags();
        last = [...tags].filter((t) => t.includes(".")).join(", ");
        return visible.every((t) => tags.has(t)) && hidden.every((t) => !tags.has(t)) ? true : null;
      },
      timeoutMs,
      1500,
    );
  } catch (error) {
    throw new Error(
      `${error instanceof Error ? error.message : String(error)} (on screen: ${last})`,
      { cause: error },
    );
  }
}

/** Waits until home on [device] shows [tag] exactly [n] times. */
async function expectCount(device: Device, tag: string, n: number, what: string) {
  await waitFor(
    `${device.role}: ${what} (${String(n)} × ${tag})`,
    async () => ((await device.countOnScreen(tag)) === n ? true : null),
    60_000,
    1500,
  );
}

/** No alarm of the app is pending and no poke is up on [device]. */
async function expectNoPokes(device: Device, timeoutMs = 60_000) {
  await waitFor(
    `${device.role}: no poke alarm and no poke on screen`,
    async () =>
      (await device.pendingPokeAlarms()) === 0 && !(await device.pokeShowing()) ? true : null,
    timeoutMs,
  );
}

async function expectAlarms(device: Device, timeoutMs = 60_000) {
  return waitFor(
    `${device.role}: poke alarms set`,
    async () => {
      const n = await device.pendingPokeAlarms();
      return n > 0 ? n : null;
    },
    timeoutMs,
  );
}

async function expectStatus(
  phone: Phone,
  taskId: string,
  ok: (t: SyncTask) => boolean,
  what: string,
  timeoutMs = 60_000,
): Promise<SyncTask> {
  return waitFor(
    `the task ${what} on the server`,
    async () => {
      const task = await phone.task(taskId);
      return task && ok(task) ? task : null;
    },
    timeoutMs,
  );
}

const status = (s: SyncTask["status"]) => (t: SyncTask) => t.status === s;

/** [only] limits the run to these case numbers (`--cases 3,4`), for debugging one case; all run without it. */
export async function twoPerson(run: RunContext, only?: ReadonlySet<number>): Promise<void> {
  const { sibel, eren } = devices;
  // Second sessions, as other phones of theirs would sign in: they read the server and send Sibel's API commands.
  const sibelApi = new Phone(await signIn(SIBEL_PHONE));
  const erenApi = new Phone(await signIn(EREN_PHONE));
  const erenId = (await sibelApi.connection("Eren")).personId;

  const bothHome = async () => {
    for (const d of [sibel, eren]) {
      await d.wakeAndUnlock();
      await d.openApp();
    }
  };

  /** A task from Sibel through the API, both phones learn it from the push; returns its id once Eren has it. */
  const sibelSends = async (title: string, deadlineMinutes: number) => {
    const taskId = await sibelApi.createTask(
      erenId,
      title,
      new Date(Date.now() + deadlineMinutes * MINUTE),
    );
    run.log(`task ${taskId} "${title}"`);
    await expectAlarms(eren);
    return taskId;
  };

  const cases: [number, string, (dir: string) => Promise<void>][] = [
    // 1. Sibel writes a task while Eren's app is in the background: his phone has the alarms and the arrival note,
    //    her home has the row; Eren opens the app and it is his next-up card. She cancels it from her other phone
    //    while his app is open: his card goes and he sees she cancelled it.
    [
      1,
      "write-while-away",
      async (dir) => {
        await bothHome();
        await eren.pressHome();
        await sibel.maestro("sibel/write-task-to-other.yaml", dir, { TEXT: WRITTEN_TEXT });
        const task = await waitFor(
          "the written task on the server",
          async () => (await erenApi.tasks()).find((t) => t.status === "SCHEDULED") ?? null,
          30_000,
        );
        await expectAlarms(eren);
        await waitFor(
          "eren: the arrival note (4b)",
          async () => ((await eren.notificationChannels()).includes("tasks") ? true : null),
          30_000,
        );
        await expectHome(sibel, "her row under Senin yazdıkların", { visible: ["home.sent"] });
        await eren.openApp();
        await expectHome(eren, "his next-up card", { visible: ["home.nextUp"] });
        const cancelledRows = await eren.countOnScreen("issue.cancelled");
        await sibelApi.cancel(task.id);
        await expectHome(eren, "no card", { hidden: ["home.nextUp"] });
        await expectCount(eren, "issue.cancelled", cancelledRows + 1, "her cancel's row");
        await expectNoPokes(eren);
        await expectHome(sibel, "no row", { hidden: ["home.sent"] });
      },
    ],

    // 2. Eren taps Şimdiden bitti: done on the server, his card, alarms and notes go, her row goes.
    [
      2,
      "done-early",
      async (dir) => {
        await bothHome();
        const taskId = await sibelSends("Çöpü at", QUIET_DEADLINE_MINUTES);
        await expectHome(eren, "his next-up card", { visible: ["home.nextUp"] });
        await expectHome(sibel, "her row", { visible: ["home.sent"] });
        await eren.maestro("common/tap.yaml", dir, { ID: "home.doneEarly" });
        await expectHome(eren, "no card after Bitti", { hidden: ["home.nextUp"] }, 15_000);
        await expectNoPokes(eren, 15_000);
        await expectStatus(erenApi, taskId, status("DONE"), "DONE");
        await expectHome(sibel, "the row gone after his Bitti", { hidden: ["home.sent"] });
        const left = await eren.notificationChannels();
        if (left.length > 0) throw new Error(`eren still has notifications: ${left.join(", ")}`);
      },
    ],

    // 3. A poke is up on Eren's phone (app in the background) when Sibel cancels it in the app: the poke goes from
    //    his phone with the alarms, her row goes, and his home says she cancelled it.
    [
      3,
      "cancel-while-poke-shows",
      async (dir) => {
        await bothHome();
        await expectHome(eren, "home", {});
        const cancelledRows = await eren.countOnScreen("issue.cancelled");
        const taskId = await sibelSends("Faturayı öde", POKE_DEADLINE_MINUTES);
        await eren.pressHome();
        await waitFor(
          "eren: a poke up",
          async () => ((await eren.pokeShowing()) ? true : null),
          120_000,
        );
        await sibel.maestro("sibel/cancel-task.yaml", dir);
        await expectStatus(erenApi, taskId, status("CANCELLED"), "CANCELLED");
        await expectNoPokes(eren);
        await expectHome(sibel, "the row gone after her cancel", { hidden: ["home.sent"] });
        await eren.openApp();
        await expectHome(eren, "no card", { hidden: ["home.nextUp"] });
        await expectCount(eren, "issue.cancelled", cancelledRows + 1, "her cancel's row");
      },
    ],

    // 4. The full screen poke rings and Eren taps Ertele: the server counts it, his card says when it rings again,
    //    his alarms stay, her row stays. Then Bitti on his card closes it on both.
    [
      4,
      "snooze",
      async (dir) => {
        await bothHome();
        const taskId = await sibelSends("Annemi ara", POKE_DEADLINE_MINUTES);
        await eren.screenOff();
        await eren.maestro("sibel/wait-poke.yaml", dir);
        await eren.maestro("common/tap.yaml", dir, { ID: "poke.snooze" });
        await expectStatus(erenApi, taskId, (t) => (t.snoozesUsed ?? 0) === 1, "snoozed once");
        await eren.wakeAndUnlock();
        await eren.openApp();
        await expectHome(eren, "the snoozed card", { visible: ["home.nextUp", "nextUp.snoozed"] });
        await expectAlarms(eren);
        if (await eren.pokeShowing()) throw new Error("eren: the poke is still up after Ertele");
        await expectHome(sibel, "her row still there", { visible: ["home.sent"] });
        await eren.maestro("common/tap.yaml", dir, { ID: "home.doneEarly" });
        await expectStatus(erenApi, taskId, status("DONE"), "DONE");
        await expectNoPokes(eren);
        await expectHome(sibel, "the row gone after his Bitti", { hidden: ["home.sent"] });
      },
    ],

    // 5. Eren answers the full screen poke with Bugün yapamayacağım: dropped, his pokes stop, her home has the
    //    dropped card. She taps Ben hallederim: the card goes on her side, nothing comes back on his.
    [
      5,
      "cant-today-then-sender-handles",
      async (dir) => {
        await bothHome();
        await expectHome(eren, "home", {});
        const cancelledRows = await eren.countOnScreen("issue.cancelled");
        const taskId = await sibelSends("Kargoyu al", POKE_DEADLINE_MINUTES);
        await eren.screenOff();
        await eren.maestro("sibel/wait-poke.yaml", dir);
        await eren.maestro("eren/poke-cant-today.yaml", dir);
        await expectStatus(erenApi, taskId, status("DROPPED"), "DROPPED");
        await expectNoPokes(eren);
        await expectHome(sibel, "the dropped card", {
          visible: ["issue.iHandle"],
          hidden: ["home.sent"],
        });
        await sibel.maestro("common/tap.yaml", dir, { ID: "issue.iHandle" });
        await expectStatus(erenApi, taskId, status("CANCELLED"), "CANCELLED (Ben hallederim)");
        await expectHome(sibel, "no dropped card", { hidden: ["issue.iHandle", "home.sent"] });
        await eren.wakeAndUnlock();
        await eren.openApp();
        await expectHome(eren, "no card", { hidden: ["home.nextUp"] });
        // Ben hallederim is not a cancel he is told about (5d).
        await expectCount(eren, "issue.cancelled", cancelledRows, "no new cancelled row");
      },
    ],

    // 6. Nobody answers until the deadline: missed on the server, the poke goes from his phone, both homes have the
    //    missed card. Eren taps Sonradan yaptım: done, and her missed card goes.
    [
      6,
      "missed-then-did-it-later",
      async (dir) => {
        await bothHome();
        const deadlineMinutes = 3;
        const deadline = Date.now() + deadlineMinutes * MINUTE;
        const taskId = await sibelSends("Çiçekleri sula", deadlineMinutes);
        await eren.pressHome();
        await waitFor(
          "eren: a poke up",
          async () => ((await eren.pokeShowing()) ? true : null),
          150_000,
        );
        await new Promise((resolve) =>
          setTimeout(resolve, Math.max(0, deadline - Date.now() + 5_000)),
        );
        await waitFor(
          "the task MISSED after a tick",
          async () => {
            await testApi.tick();
            return (await erenApi.task(taskId))?.status === "MISSED" ? true : null;
          },
          60_000,
          3000,
        );
        await expectNoPokes(eren);
        await expectHome(sibel, "her missed card", {
          visible: ["issue.newTime", "issue.cancel"],
          hidden: ["home.sent"],
        });
        await eren.openApp();
        await expectHome(eren, "his missed card", {
          visible: ["issue.didItLater"],
          hidden: ["home.nextUp"],
        });
        await eren.maestro("common/tap.yaml", dir, { ID: "issue.didItLater" });
        await expectStatus(erenApi, taskId, status("DONE"), "DONE (Sonradan yaptım)");
        await expectHome(eren, "no missed card", { hidden: ["issue.didItLater"] }, 15_000);
        await expectHome(sibel, "no missed card", { hidden: ["issue.newTime", "issue.cancel"] });
      },
    ],

    // 7. Sibel moves the deadline of a task whose pokes are far away to 90 minutes from now: Eren's phone takes the
    //    new plan, and its first poke rings within two minutes. Then she cancels; nothing is left on his phone.
    [
      7,
      "edit-deadline",
      async () => {
        await bothHome();
        const taskId = await sibelSends("Bulaşığı yerleştir", QUIET_DEADLINE_MINUTES);
        const before = await erenApi.task(taskId);
        await sibelApi.editDeadline(taskId, new Date(Date.now() + POKE_DEADLINE_MINUTES * MINUTE));
        await expectStatus(
          erenApi,
          taskId,
          (t) => t.planVersion > (before?.planVersion ?? 0),
          "with a new plan version",
        );
        await eren.pressHome();
        await waitFor(
          "eren: a poke of the new plan",
          async () => ((await eren.pokeShowing()) ? true : null),
          120_000,
        );
        await sibelApi.cancel(taskId);
        await expectNoPokes(eren);
        await eren.openApp();
        await expectHome(eren, "no card", { hidden: ["home.nextUp"] });
        await expectHome(sibel, "no row", { hidden: ["home.sent"] });
      },
    ],
  ];

  for (const [n, name, fn] of cases) {
    if (only === undefined || only.has(n)) await run.step(`sync-${String(n)}-${name}`, fn);
  }
}
