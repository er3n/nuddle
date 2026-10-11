// E2E orchestrator (docs/testing.md, Orchestrator; ADR 0012). `mise run e2e:smoke` runs the smoke set:
//   1. local Supabase, both emulators (fixed ports first: Gradle can take ports from the ephemeral range)
//   2. a fresh `nuddle_e2e` database and the backend in E2E mode on :3000
//   3. the e2e build installed clean on both emulators, logcat streaming
//   4. the smoke flows, screenshots of both devices after every step
// Prints the run folder first, stops itself after a hard timeout, exits non-zero on failure.
// Usage: tsx src/run.ts --set smoke [--cold]
import type { ChildProcess } from "node:child_process";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { testApi } from "./api";
import {
  localSupabase,
  recreateDatabase,
  requireFirebaseServiceAccount,
  startBackend,
  stopBackend,
} from "./backend";
import { repoRoot, RunContext } from "./context";
import { allDevices } from "./devices";
import { run, waitFor } from "./proc";
import { taskToSelf } from "./smoke/task-to-self";

const TIMEOUT_MS = 20 * 60_000;
const APK = join(
  repoRoot,
  "apps",
  "android",
  "app",
  "build",
  "outputs",
  "apk",
  "e2e",
  "debug",
  "app-e2e-debug.apk",
);

const { values } = parseArgs({
  options: {
    set: { type: "string", default: "smoke" },
    cold: { type: "boolean", default: false },
  },
});
if (values.set !== "smoke") {
  console.error(`unknown set "${values.set}"; only "smoke" exists yet`);
  process.exit(2);
}

const ctx = new RunContext(new Date());
console.log(`run folder: ${ctx.dir}`);
const startedAt = new Date().toISOString();
let backend: ChildProcess | null = null;

function cleanup(): void {
  for (const d of allDevices()) d.stopLogcat();
  if (backend) stopBackend(backend);
  backend = null;
}

function finish(status: "passed" | "failed" | "timed out", error?: string): never {
  cleanup();
  ctx.writeJson("result.json", {
    status,
    set: values.set,
    startedAt,
    finishedAt: new Date().toISOString(),
    ...(error === undefined ? {} : { error }),
    push: "fcm",
    steps: ctx.steps,
  });
  ctx.log(`${values.set}: ${status.toUpperCase()}`);
  console.log(`run folder: ${ctx.dir}`);
  process.exit(status === "passed" ? 0 : 1);
}

const hardStop = setTimeout(() => {
  finish("timed out", `hard timeout after ${String(TIMEOUT_MS / 60_000)} minutes`);
}, TIMEOUT_MS);
hardStop.unref();
process.on("SIGINT", () => finish("failed", "interrupted"));
process.on("SIGTERM", () => finish("failed", "terminated"));

ctx.afterStep = async (stepDir) => {
  await Promise.all(
    allDevices().map((d) => d.screenshot(join(stepDir, `${d.role}.png`)).catch(() => undefined)),
  );
};

async function main(): Promise<void> {
  requireFirebaseServiceAccount();
  const supabase = await ctx.step("supabase", () =>
    localSupabase((m) => {
      ctx.log(m);
    }),
  );

  await ctx.step("emulators", () =>
    run(
      join(repoRoot, "e2e", "scripts", "emulators.sh"),
      values.cold ? ["boot", "--cold"] : ["boot"],
      {
        timeoutMs: 10 * 60_000,
        logFile: ctx.path("emulators.log"),
      },
    ),
  );

  const databaseUrl = await ctx.step("database", () => recreateDatabase(supabase.dbUrl));

  await ctx.step("backend", async () => {
    backend = await startBackend(supabase, databaseUrl, ctx.path("server.log"));
    // The first request compiles the route; a 404 here means the server is not in E2E mode.
    await waitFor(
      "the backend in E2E mode (POST /api/test/reset)",
      () => testApi.reset(),
      180_000,
      2000,
    );
  });

  await ctx.step("build", () =>
    run("./gradlew", ["--console=plain", "assembleE2eDebug"], {
      cwd: join(repoRoot, "apps", "android"),
      timeoutMs: 15 * 60_000,
      logFile: ctx.path("gradle.log"),
    }),
  );

  await ctx.step("install", async () => {
    for (const d of allDevices()) {
      await d.installClean(APK);
      d.startLogcat(ctx.path(`logcat-${d.role}.txt`));
    }
  });

  await taskToSelf(ctx, databaseUrl);
}

main().then(
  () => {
    finish("passed");
  },
  (error: unknown) => {
    finish("failed", error instanceof Error ? error.message : String(error));
  },
);
