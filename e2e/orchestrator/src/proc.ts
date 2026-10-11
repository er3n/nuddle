// Child processes: short commands with captured output, and long-lived ones streamed to a file.
import { spawn, type ChildProcess } from "node:child_process";
import { createWriteStream } from "node:fs";

export interface RunOptions {
  cwd?: string;
  env?: NodeJS.ProcessEnv;
  /** Kill the command after this many milliseconds. */
  timeoutMs?: number;
  /** Also append stdout and stderr to this file. */
  logFile?: string;
  /** Resolve with the output instead of throwing on a non-zero exit. */
  allowFailure?: boolean;
}

export interface RunResult {
  code: number | null;
  stdout: string;
  /** The raw stdout, for binary output such as `screencap -p`. */
  stdoutBytes: Buffer;
  stderr: string;
}

export function run(
  cmd: string,
  args: readonly string[],
  opts: RunOptions = {},
): Promise<RunResult> {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, {
      cwd: opts.cwd,
      env: opts.env ?? process.env,
      stdio: ["ignore", "pipe", "pipe"],
    });
    const log = opts.logFile === undefined ? null : createWriteStream(opts.logFile, { flags: "a" });
    const chunks: Buffer[] = [];
    let stderr = "";
    child.stdout.on("data", (d: Buffer) => {
      chunks.push(d);
      log?.write(d);
    });
    child.stderr.on("data", (d: Buffer) => {
      stderr += d.toString();
      log?.write(d);
    });
    const timer =
      opts.timeoutMs === undefined
        ? null
        : setTimeout(() => {
            child.kill("SIGKILL");
          }, opts.timeoutMs);
    child.on("error", (error) => {
      if (timer) clearTimeout(timer);
      log?.end();
      reject(error);
    });
    child.on("close", (code) => {
      if (timer) clearTimeout(timer);
      log?.end();
      const stdoutBytes = Buffer.concat(chunks);
      const stdout = stdoutBytes.toString();
      const result = { code, stdout, stdoutBytes, stderr };
      if (code === 0 || opts.allowFailure === true) {
        resolve(result);
      } else {
        const tail = (stderr || stdout).trim().split("\n").slice(-20).join("\n");
        reject(new Error(`${cmd} ${args.join(" ")} exited with ${String(code)}\n${tail}`));
      }
    });
  });
}

/** A long-running process whose output goes to `file`; stop it with `stop()`. */
export function spawnLogged(
  cmd: string,
  args: readonly string[],
  file: string,
  opts: { cwd?: string; env?: NodeJS.ProcessEnv } = {},
): ChildProcess {
  const out = createWriteStream(file, { flags: "a" });
  const child = spawn(cmd, args, {
    cwd: opts.cwd,
    env: opts.env ?? process.env,
    stdio: ["ignore", "pipe", "pipe"],
    // Own process group, so stopping it also stops what it started (pnpm starts next).
    detached: true,
  });
  child.stdout.pipe(out);
  child.stderr.pipe(out);
  return child;
}

export function stop(child: ChildProcess): void {
  if (child.pid === undefined || child.exitCode !== null) return;
  try {
    process.kill(-child.pid, "SIGTERM");
  } catch {
    // Already gone.
  }
}

const sleep = (ms: number): Promise<void> =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

/** Polls `check` until it returns a value other than null, or throws after `timeoutMs`. */
export async function waitFor<T>(
  what: string,
  check: () => Promise<T | null>,
  timeoutMs: number,
  intervalMs = 1000,
): Promise<T> {
  const until = Date.now() + timeoutMs;
  let lastError: unknown = null;
  for (;;) {
    try {
      const value = await check();
      if (value !== null) return value;
    } catch (error) {
      lastError = error;
    }
    if (Date.now() > until) {
      const cause = lastError instanceof Error ? `: ${lastError.message}` : "";
      throw new Error(`timed out after ${String(timeoutMs / 1000)}s waiting for ${what}${cause}`);
    }
    await sleep(intervalMs);
  }
}
