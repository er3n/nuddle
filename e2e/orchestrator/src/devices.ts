// The two emulators (docs/testing.md, Orchestrator): emulator-5554 is Sibel, emulator-5556 is Eren.
// adb for everything (ADR 0018): setup, system conditions, screenshots, the screen and fake time.
import type { ChildProcess } from "node:child_process";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { run, sleep, spawnLogged, stop } from "./proc";
import { describe, matches, parseDump, type Target, type UiNode } from "./ui";

const APP_ID = "app.nuddle.debug";
/** Where a warm start remembers which APK it installed (docs/testing.md, Warm start). */
const APK_SHA = "/data/local/tmp/nuddle-e2e-apk.sha";
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
    await this.noKeyboard();
    await this.reversePorts();
  }

  /**
   * Warm start (docs/testing.md, Warm start): the app stays with its data, signed in; it is replaced (`install -r`)
   * only when the APK changed since the last run on this device.
   */
  async installKeepingData(apk: string): Promise<void> {
    const sha = createHash("sha256").update(readFileSync(apk)).digest("hex");
    const installed = await this.shell(`cat ${APK_SHA} 2>/dev/null; true`);
    if (installed.trim() !== sha || !(await this.installed())) {
      await this.adb(["install", "-r", "-g", apk], 180_000);
      await this.shell(`echo ${sha} > ${APK_SHA}`);
    }
    await this.shell(`appops set ${APP_ID} USE_FULL_SCREEN_INTENT allow`);
    await this.noKeyboard();
    await this.reversePorts();
  }

  private async installed(): Promise<boolean> {
    return (await this.shell(`pm path ${APP_ID}; true`)).includes("package:");
  }

  /** Whether the app is installed and has a signed-in session (its DataStore file). */
  async signedIn(): Promise<boolean> {
    if (!(await this.installed())) return false;
    const files = await this.shell(`run-as ${APP_ID} ls files/datastore 2>/dev/null; true`);
    return files.includes("session.preferences_pb");
  }

  /**
   * Sends one of the e2e flavor's commands (apps/android e2e/E2eReceiver.kt) and waits for its result; returns its
   * data. The app process starts for it if it is not running.
   */
  async e2e(action: string, extras: string[] = []): Promise<string> {
    const out = await this.shell(
      `am broadcast -a app.nuddle.e2e.${action} -n ${APP_ID}/app.nuddle.e2e.E2eReceiver ${extras.join(" ")}`,
      30_000,
    );
    const data = /data="([^"]*)"/.exec(out)?.[1];
    if (!/result=-1/.test(out) || data === undefined || data.startsWith("error"))
      throw new Error(`e2e ${action} on ${this.role}: ${out.trim()}`);
    return data;
  }

  /** The app's clock moves on by [ms]; steps that came due ring at once (fake time). */
  async advance(ms: number): Promise<void> {
    await this.e2e("ADVANCE", ["--el", "ms", String(ms)]);
  }

  /**
   * As [advance], for an app that is force stopped: the offset is written where the app keeps it
   * (`shared_prefs/e2e_time.xml`, apps/android di/TestTime.kt) and nothing starts the app.
   */
  async advanceStopped(ms: number): Promise<void> {
    const file = "shared_prefs/e2e_time.xml";
    const now = await this.shell(`run-as ${APP_ID} cat ${file} 2>/dev/null; true`);
    const offset = Number(/name="offsetMs" value="(-?\d+)"/.exec(now)?.[1] ?? "0") + ms;
    const xml = `<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <long name="offsetMs" value="${String(offset)}" />\n</map>\n`;
    const local = join(tmpdir(), `nuddle-e2e-time-${this.role}.xml`);
    writeFileSync(local, xml);
    await this.adb(["push", local, "/data/local/tmp/e2e_time.xml"]);
    await this.shell(
      `run-as ${APP_ID} sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/e2e_time.xml ${file}'`,
    );
  }

  /** No task on the phone, real time again, a full sync (between cases of a warm run). */
  async resetApp(): Promise<void> {
    const data = await this.e2e("RESET");
    if (data !== "synced true") throw new Error(`reset on ${this.role}: ${data}`);
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

  /** Starts the app anew, on its start screen, as Maestro's `launchApp` did (it stops the app first). */
  async relaunch(): Promise<void> {
    await this.forceStop();
    await this.openApp();
  }

  /** Brings the app to the front (a running activity is reused, as a tap on the launcher icon does). */
  async openApp(): Promise<void> {
    await this.shell(`monkey -p ${APP_ID} -c android.intent.category.LAUNCHER 1`);
  }

  /** The screen now, from a UI Automator dump; a dump taken while the screen animates can fail and is tried again. */
  async screen(): Promise<UiNode[]> {
    for (let attempt = 0; ; attempt += 1) {
      const out = await this.shell(
        "uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; cat /data/local/tmp/ui.xml",
      );
      if (out.includes("<hierarchy")) {
        this.lastDump = out;
        return parseDump(out);
      }
      if (attempt >= 4) throw new Error(`no UI dump from ${this.role}`);
      await sleep(1000);
    }
  }

  /** The last dump taken. */
  lastDump = "";

  /** The screen's hierarchy into [file], for the step's folder (what a flow could see). */
  async dumpTo(file: string): Promise<void> {
    await this.screen();
    writeFileSync(file, this.lastDump);
  }

  /** The test tags on screen now. */
  async screenTags(): Promise<Set<string>> {
    return new Set((await this.screen()).map((n) => n.tag).filter((t) => t !== ""));
  }

  /** Waits until [target] is on screen; returns its node. */
  async see(target: Target, timeoutMs = 30_000): Promise<UiNode> {
    const until = Date.now() + timeoutMs;
    for (;;) {
      const node = (await this.screen()).find((n) => matches(n, target));
      if (node) return node;
      if (Date.now() > until) throw new Error(`${this.role}: ${describe(target)} not on screen`);
      await sleep(500);
    }
  }

  /** Waits until [target] is no longer on screen. */
  async gone(target: Target, timeoutMs = 30_000): Promise<void> {
    const until = Date.now() + timeoutMs;
    while ((await this.screen()).some((n) => matches(n, target))) {
      if (Date.now() > until) throw new Error(`${this.role}: ${describe(target)} still on screen`);
      await sleep(500);
    }
  }

  /** Fails at once when [target] is on screen. */
  async notThere(target: Target): Promise<void> {
    if ((await this.screen()).some((n) => matches(n, target)))
      throw new Error(`${this.role}: ${describe(target)} on screen`);
  }

  /** Taps [target] once it is on screen. */
  async tap(target: Target, timeoutMs = 30_000): Promise<void> {
    const node = await this.see(target, timeoutMs);
    await this.shell(`input tap ${String(node.x)} ${String(node.y)}`);
  }

  /** Taps [targets] one after the other, each once it is on screen. */
  async taps(...targets: Target[]): Promise<void> {
    for (const target of targets) await this.tap(target);
  }

  /**
   * Taps the field [tag] and types ASCII [text] into it. There is no on-screen keyboard (see [noKeyboard]), so the
   * keys go straight into the field. What landed in it is checked ([check]: off for a field that submits itself
   * when full); a wrong one is typed again. The field is emptied first.
   */
  async type(tag: string, text: string, check = true): Promise<void> {
    await this.tap(tag);
    for (let attempt = 1; ; attempt += 1) {
      await sleep(300);
      // Whatever the field holds goes first: select all, delete.
      await this.shell("input keycombination KEYCODE_CTRL_LEFT KEYCODE_A");
      await this.shell("input keyevent KEYCODE_DEL");
      await this.typeText(text);
      if (!check) return;
      const field = (await this.screen()).find((n) => n.tag === tag)?.text ?? "";
      // Fields may format what is typed (the phone number gets spaces).
      const bare = (v: string) => v.replace(/[^\p{L}\p{N}]/gu, "");
      if (bare(field) === bare(text)) return;
      if (attempt >= 3)
        throw new Error(`${this.role}: ${tag} says "${field}" after typing "${text}"`);
    }
  }

  /**
   * Turns the on-screen keyboards off (ADR 0019): Gboard autocorrects what `input text` types and covers half the
   * screen; without a keyboard, Compose fields take the keys as from a hardware keyboard. Kept across runs.
   */
  async noKeyboard(): Promise<void> {
    const enabled = (await this.shell("ime list -s")).split("\n").filter((l) => l.trim() !== "");
    for (const ime of enabled) await this.shell(`ime disable ${ime.trim()}`);
  }

  /** Types ASCII [text] into the focused field. */
  async typeText(text: string): Promise<void> {
    await this.shell(`input text '${text.replace(/[^A-Za-z0-9 ]/g, "").replaceAll(" ", "%s")}'`);
  }

  /** Closes the keyboard when it is open (Back would leave the screen otherwise). */
  async hideKeyboard(): Promise<void> {
    if ((await this.shell("dumpsys input_method")).includes("mInputShown=true")) await this.back();
  }

  async back(): Promise<void> {
    await this.shell("input keyevent KEYCODE_BACK");
  }

  /** Scrolls the screen up until [tag] is on it. */
  async scrollTo(tag: string): Promise<void> {
    for (let i = 0; i < 8; i += 1) {
      if ((await this.screen()).some((n) => n.tag === tag)) return;
      await this.shell("input swipe 540 1800 540 700 300");
    }
    await this.see(tag, 1000);
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
}

export const devices = {
  sibel: new Device("sibel", "emulator-5554"),
  eren: new Device("eren", "emulator-5556"),
};

export const allDevices = (): Device[] => [devices.sibel, devices.eren];
