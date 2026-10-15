// The two emulators (docs/testing.md, Orchestrator): emulator-5554 is Sibel, emulator-5556 is Eren.
// adb for setup, system conditions and screenshots; Maestro for the flows in e2e/flows/.
import type { ChildProcess } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { repoRoot } from "./context";
import { run, spawnLogged, stop } from "./proc";

const APP_ID = "app.nuddle.debug";
/** The poke alarm's broadcast action (apps/android alarm/PokeIntents.kt, ACTION_FIRE). */
const POKE_FIRE_TAG = "*walarm*:app.nuddle.action.POKE_FIRE";

const androidHome = (): string => {
  const home = process.env.ANDROID_HOME;
  if (home === undefined || home === "")
    throw new Error("ANDROID_HOME is not set; run through mise");
  return home;
};

const adbPath = (): string => join(androidHome(), "platform-tools", "adb");

export type Role = "sibel" | "eren";

export class Device {
  private logcat: ChildProcess | null = null;
  /** Set by [offline]: the API is out of reach until [online]. */
  private cutOff = false;

  constructor(
    readonly role: Role,
    readonly serial: string,
  ) {}

  adb(args: readonly string[], timeoutMs = 60_000): Promise<string> {
    return run(adbPath(), ["-s", this.serial, ...args], { timeoutMs }).then((r) => r.stdout);
  }

  shell(command: string, timeoutMs = 60_000): Promise<string> {
    return this.adb(["shell", command], timeoutMs);
  }

  /** Clean state for the app: uninstall, install with runtime permissions, allow full screen pokes. */
  async installClean(apk: string): Promise<void> {
    await run(adbPath(), ["-s", this.serial, "uninstall", APP_ID], { allowFailure: true });
    // -g grants runtime permissions (POST_NOTIFICATIONS); setup screens 2c to 2e arrive in slice 1.
    await this.adb(["install", "-r", "-g", apk], 180_000);
    await this.shell(`appops set ${APP_ID} USE_FULL_SCREEN_INTENT allow`);
    await this.reversePorts();
  }

  /**
   * The API on :3000, and local Supabase on :54321, where the signed photo upload and view URLs point. Per adb
   * transport: a reconnected emulator loses them.
   */
  private async reversePorts(): Promise<void> {
    if (this.cutOff) return;
    await this.adb(["reverse", "tcp:3000", "tcp:3000"]);
    await this.adb(["reverse", "tcp:54321", "tcp:54321"]);
  }

  /** The photo the e2e flavor's camera takes (InjectedPhotoSource, NUD-29): into the app's own files. */
  async injectPhoto(jpeg: string): Promise<void> {
    await this.adb(["push", jpeg, "/data/local/tmp/e2e-photo.jpg"]);
    await this.shell(
      `run-as ${APP_ID} sh -c 'mkdir -p files && cp /data/local/tmp/e2e-photo.jpg files/e2e-photo.jpg'`,
    );
  }

  /** What the e2e flavor's speech input "hears" (InjectedSpeechInput, 4h): into the app's own files. */
  async injectSpeech(text: string): Promise<void> {
    const quoted = text.replace(/'/g, "");
    await this.shell(
      `run-as ${APP_ID} sh -c 'mkdir -p files && echo "${quoted}" > files/e2e-speech.txt'`,
    );
  }

  startLogcat(file: string): void {
    void run(adbPath(), ["-s", this.serial, "logcat", "-c"], { allowFailure: true });
    this.logcat = spawnLogged(adbPath(), ["-s", this.serial, "logcat", "-v", "threadtime"], file);
  }

  stopLogcat(): void {
    if (this.logcat) stop(this.logcat);
    this.logcat = null;
  }

  async screenshot(file: string): Promise<void> {
    const r = await run(adbPath(), ["-s", this.serial, "exec-out", "screencap", "-p"], {
      timeoutMs: 30_000,
    });
    writeFileSync(file, r.stdoutBytes);
  }

  async wakeAndUnlock(): Promise<void> {
    await this.shell("input keyevent KEYCODE_WAKEUP");
    await this.shell("wm dismiss-keyguard");
  }

  /** Force stop, as a phone brand's battery manager does: the app's alarms go with it. */
  async forceStop(): Promise<void> {
    await this.shell(`am force-stop ${APP_ID}`);
  }

  /**
   * No network (6a): airplane mode, and the adb reverse ports gone too, since the app reaches the API through them and
   * they work without a network.
   */
  async offline(): Promise<void> {
    this.cutOff = true;
    await this.shell("cmd connectivity airplane-mode enable");
    await this.adb(["reverse", "--remove-all"]);
  }

  async online(): Promise<void> {
    this.cutOff = false;
    await this.reversePorts();
    await this.shell("cmd connectivity airplane-mode disable");
  }

  /** Screen off: the full screen intent opens the poke over the lock screen only when the screen is off. */
  async screenOff(): Promise<void> {
    await this.shell("input keyevent KEYCODE_SLEEP");
  }

  /**
   * Pending poke alarms of the app (`setAlarmClock`, one per step). Counts only pending entries
   * (`RTC_WAKEUP #n: Alarm{...}` followed by its tag); the stats further down repeat the tag for
   * alarms that already fired.
   */
  async pendingPokeAlarms(): Promise<number> {
    const lines = (await this.shell("dumpsys alarm")).split("\n");
    return lines.filter(
      (line, i) =>
        /#\d+: Alarm\{/.test(line) && (lines[i + 1] ?? "").includes(`tag=${POKE_FIRE_TAG}`),
    ).length;
  }

  /** Home key: the app goes to the background, as when the person puts the phone away. */
  async pressHome(): Promise<void> {
    await this.shell("input keyevent KEYCODE_HOME");
  }

  /** Brings the app to the front (a running activity is reused, as a tap on the launcher icon does). */
  async openApp(): Promise<void> {
    await this.shell(`monkey -p ${APP_ID} -c android.intent.category.LAUNCHER 1`);
  }

  /**
   * The test tags on screen right now (resource ids in the `e2e` flavor), from a UI Automator dump. A dump taken
   * while the screen animates can fail; it is tried again.
   */
  async screenTags(): Promise<Set<string>> {
    return new Set(await this.screenTagList());
  }

  /** How many elements on screen carry [tag] (one per row for row tags). */
  async countOnScreen(tag: string): Promise<number> {
    return (await this.screenTagList()).filter((t) => t === tag).length;
  }

  private async screenTagList(): Promise<string[]> {
    for (let attempt = 0; ; attempt += 1) {
      const out = await this.shell(
        "uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; cat /data/local/tmp/ui.xml",
      );
      if (out.includes("<hierarchy")) {
        return [...out.matchAll(/resource-id="([^"]+)"/g)].map((m) => m[1] ?? "");
      }
      if (attempt >= 4) throw new Error(`no UI dump from ${this.role}`);
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }

  /** The app's notifications that are up now, by channel (`poke_alarm`, `poke_silent`, `tasks`, ...). */
  async notificationChannels(): Promise<string[]> {
    const out = await this.shell("dumpsys notification --noredact");
    return out
      .split("\n")
      .filter((line) => line.includes("NotificationRecord(") && line.includes(`pkg=${APP_ID} `))
      .map((line) => /Notification\(channel=(\S+)/.exec(line)?.[1] ?? "?");
  }

  /** Whether a poke notification (3a) or the full screen poke (3b) of the app is up. */
  async pokeShowing(): Promise<boolean> {
    return (await this.notificationChannels()).some((c) => c.startsWith("poke_"));
  }

  /** Runs a Maestro flow from e2e/flows on this device; its debug output goes to `outDir`. */
  async maestro(
    flow: string,
    outDir: string,
    env: Record<string, string> = {},
    timeoutMs = 300_000,
  ): Promise<void> {
    mkdirSync(outDir, { recursive: true });
    await this.reversePorts();
    const envArgs = Object.entries(env).flatMap(([k, v]) => ["-e", `${k}=${v}`]);
    const cliLog = join(outDir, "maestro-cli.log");
    // Maestro sometimes does not find a connected emulator ("was requested, but it is not connected") when adb lists
    // other devices going offline; nothing ran on the device then, so the flow is started again.
    for (let attempt = 1; ; attempt += 1) {
      try {
        await this.runMaestro(flow, outDir, envArgs, timeoutMs, cliLog);
        return;
      } catch (error) {
        const notFound =
          existsSync(cliLog) && readFileSync(cliLog, "utf8").includes("but it is not connected");
        if (!notFound || attempt >= 3) throw error;
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    }
  }

  private async runMaestro(
    flow: string,
    outDir: string,
    envArgs: string[],
    timeoutMs: number,
    cliLog: string,
  ): Promise<void> {
    await run(
      "maestro",
      [
        // Without a platform Maestro also lists iOS simulators, which can hang on a Mac.
        "--platform",
        "android",
        "--device",
        this.serial,
        "test",
        join(repoRoot, "e2e", "flows", flow),
        "--debug-output",
        outDir,
        "--flatten-debug-output",
        ...envArgs,
      ],
      {
        timeoutMs,
        // Maestro writes its own maestro.log into outDir.
        logFile: cliLog,
        env: {
          ...process.env,
          MAESTRO_CLI_NO_ANALYTICS: "1",
          MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED: "true",
        },
      },
    );
  }
}

export const devices = {
  sibel: new Device("sibel", "emulator-5554"),
  eren: new Device("eren", "emulator-5556"),
};

export const allDevices = (): Device[] => [devices.sibel, devices.eren];
