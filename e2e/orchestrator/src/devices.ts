// The two emulators (docs/testing.md, Orchestrator): emulator-5554 is Selin, emulator-5556 is Eren.
// adb for setup, system conditions and screenshots; Maestro for the flows in e2e/flows/.
import type { ChildProcess } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
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

export type Role = "selin" | "eren";

export class Device {
  private logcat: ChildProcess | null = null;

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
    await this.adb(["reverse", "tcp:3000", "tcp:3000"]);
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

  /** Runs a Maestro flow from e2e/flows on this device; its debug output goes to `outDir`. */
  async maestro(
    flow: string,
    outDir: string,
    env: Record<string, string> = {},
    timeoutMs = 300_000,
  ): Promise<void> {
    mkdirSync(outDir, { recursive: true });
    // The reverse port is per adb transport; a reconnected emulator loses it.
    await this.adb(["reverse", "tcp:3000", "tcp:3000"]);
    const envArgs = Object.entries(env).flatMap(([k, v]) => ["-e", `${k}=${v}`]);
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
        logFile: join(outDir, "maestro-cli.log"),
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
  selin: new Device("selin", "emulator-5554"),
  eren: new Device("eren", "emulator-5556"),
};

export const allDevices = (): Device[] => [devices.selin, devices.eren];
