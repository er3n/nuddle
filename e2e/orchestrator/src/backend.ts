// The backend in E2E mode (docs/architecture.md, E2E mode): `next dev` with E2E=1 on :3000, against
// its own database `nuddle_e2e` in the local Supabase Postgres ("same local stack, separate database").
// Auth users live in the stack's Auth service and survive a reset; /api/test/reset clears our tables.
import type { ChildProcess } from "node:child_process";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { connect } from "node:net";
import { join } from "node:path";
import postgres from "postgres";
import { repoRoot } from "./context";
import { run, spawnLogged, stop } from "./proc";

const API_PORT = 3000;
export const API_URL = `http://localhost:${String(API_PORT)}`;
const E2E_DATABASE = "nuddle_e2e";

export interface LocalSupabase {
  apiUrl: string;
  secretKey: string;
  dbUrl: string;
}

/** URLs and keys of the running local stack (local defaults, not secrets). Starts it when needed. */
export async function localSupabase(log: (m: string) => void): Promise<LocalSupabase> {
  const status = await run("supabase", ["status", "-o", "env"], {
    cwd: repoRoot,
    allowFailure: true,
  });
  if (status.code !== 0) {
    log("local Supabase is not running; starting it");
    await run("supabase", ["start"], { cwd: repoRoot, timeoutMs: 600_000 });
    return localSupabase(log);
  }
  const env = new Map<string, string>();
  for (const line of status.stdout.split("\n")) {
    const m = /^([A-Z_]+)="?(.*?)"?$/.exec(line);
    if (m?.[1] !== undefined && m[2] !== undefined) env.set(m[1], m[2]);
  }
  const apiUrl = env.get("API_URL");
  const secretKey = env.get("SECRET_KEY");
  const dbUrl = env.get("DB_URL");
  if (apiUrl === undefined || secretKey === undefined || dbUrl === undefined) {
    throw new Error("`supabase status` did not report API_URL, SECRET_KEY and DB_URL");
  }
  return { apiUrl, secretKey, dbUrl };
}

const withDatabase = (url: string, name: string): string => {
  const u = new URL(url);
  u.pathname = `/${name}`;
  return u.toString();
};

/** Drops and re-creates `nuddle_e2e` from supabase/migrations (no seed: /api/test/seed covers it). */
export async function recreateDatabase(adminUrl: string): Promise<string> {
  const admin = postgres(adminUrl, { max: 1, onnotice: () => undefined });
  try {
    await admin.unsafe(`drop database if exists ${E2E_DATABASE} with (force)`);
    await admin.unsafe(`create database ${E2E_DATABASE}`);
  } finally {
    await admin.end();
  }
  const url = withDatabase(adminUrl, E2E_DATABASE);
  const db = postgres(url, { max: 1, onnotice: () => undefined });
  try {
    const dir = join(repoRoot, "supabase", "migrations");
    const files = readdirSync(dir)
      .filter((f) => /^\d+_.+\.sql$/.test(f))
      .sort();
    for (const file of files) await db.unsafe(readFileSync(join(dir, file), "utf8"));
  } finally {
    await db.end();
  }
  return url;
}

/** The FCM service account must be set: pokes reach the device through a real push (NUD-10). */
export function requireFirebaseServiceAccount(): void {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) return;
  const envLocal = join(repoRoot, "apps", "web", ".env.local");
  const inFile =
    existsSync(envLocal) &&
    readFileSync(envLocal, "utf8")
      .split("\n")
      .some((line) => /^FIREBASE_SERVICE_ACCOUNT=\S/.test(line));
  if (!inFile) {
    throw new Error(
      "FIREBASE_SERVICE_ACCOUNT is not set. Add the nuddle-app-er3n service account key (one-line JSON) to apps/web/.env.local; see e2e/README.md.",
    );
  }
}

const portTaken = (port: number): Promise<boolean> =>
  new Promise((resolve) => {
    const socket = connect({ host: "127.0.0.1", port });
    socket.once("connect", () => {
      socket.destroy();
      resolve(true);
    });
    socket.once("error", () => {
      resolve(false);
    });
  });

export async function startBackend(
  supabase: LocalSupabase,
  databaseUrl: string,
  logFile: string,
  /** The run's extraction fixtures (E2E_EXTRACTOR_FIXTURES). */
  extractionsFile: string,
): Promise<ChildProcess> {
  if (await portTaken(API_PORT)) {
    throw new Error(
      `port ${String(API_PORT)} is taken (is \`mise run dev\` running?). Stop it; E2E runs its own backend.`,
    );
  }
  // Process env wins over apps/web/.env.local in Next, so these override any local values there.
  return spawnLogged(
    "pnpm",
    ["--filter", "@nuddle/web", "exec", "next", "dev", "--port", String(API_PORT)],
    logFile,
    {
      cwd: repoRoot,
      env: {
        ...process.env,
        E2E: "1",
        DATABASE_URL: databaseUrl,
        E2E_EXTRACTOR_FIXTURES: extractionsFile,
        SUPABASE_URL: supabase.apiUrl,
        SUPABASE_SECRET_KEY: supabase.secretKey,
        NEXT_TELEMETRY_DISABLED: "1",
      },
    },
  );
}

export const stopBackend = stop;
