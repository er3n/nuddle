// Test databases (docs/testing.md, Server integration). Each integration file gets its own database
// `nuddle_test_<id>` in the local Supabase Postgres, migrated from supabase/migrations/, and
// dropped afterwards. HTTP and scenario tests copy a template migrated once per run (much faster
// than migrating per test). Needs `supabase start` (or `mise run dev`).
import { randomUUID } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import { drizzle, type PostgresJsDatabase } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "../../src/infra/db/schema";

// Local Supabase defaults; not a secret (supabase/config.toml, db.port).
const LOCAL_URL = "postgresql://postgres:postgres@127.0.0.1:54322/postgres";
const migrationsDir = new URL("../../../../supabase/migrations/", import.meta.url);

const localDatabaseUrl = process.env.TEST_DATABASE_URL ?? LOCAL_URL;

export interface TestDatabase {
  db: PostgresJsDatabase<typeof schema>;
  sql: postgres.Sql;
  drop: () => Promise<void>;
}

function withDatabase(url: string, name: string): string {
  const u = new URL(url);
  u.pathname = `/${name}`;
  return u.toString();
}

export async function connectAdmin(): Promise<postgres.Sql> {
  const admin = postgres(localDatabaseUrl, { max: 1, onnotice: () => undefined });
  try {
    await admin`select 1`;
  } catch (cause) {
    await admin.end();
    throw new Error(
      `Local Supabase Postgres is not reachable at ${new URL(localDatabaseUrl).host}. Run \`supabase start\` or \`mise run dev\`.`,
      { cause },
    );
  }
  return admin;
}

async function applyMigrations(sql: postgres.Sql): Promise<void> {
  const files = (await readdir(migrationsDir)).filter((f) => /^\d+_.+\.sql$/.test(f)).sort();
  for (const file of files) {
    const text = await readFile(new URL(file, migrationsDir), "utf8");
    await sql.unsafe(text);
  }
}

export async function createTestDatabase(): Promise<TestDatabase> {
  const name = `nuddle_test_${randomUUID().replaceAll("-", "")}`;
  const admin = await connectAdmin();
  await admin.unsafe(`create database ${name}`);
  const t = open(name, admin);
  try {
    await applyMigrations(t.sql);
  } catch (error) {
    await t.drop();
    throw error;
  }
  return t;
}

function open(name: string, admin: postgres.Sql): TestDatabase {
  const sql = postgres(withDatabase(localDatabaseUrl, name), { max: 4, onnotice: () => undefined });
  const drop = async () => {
    await sql.end();
    await admin.unsafe(`drop database if exists ${name} with (force)`);
    await admin.end();
  };
  return { db: drizzle({ client: sql, schema }), sql, drop };
}

/** A migrated database to copy from, created once per run (test/support/global-setup.ts). */
export async function createTemplateDatabase(): Promise<{
  name: string;
  drop: () => Promise<void>;
}> {
  const t = await createTestDatabase();
  const [row] = await t.sql<{ name: string }[]>`select current_database() as name`;
  // A template may have no open connections while it is copied.
  await t.sql.end();
  return { name: row?.name ?? "", drop: t.drop };
}

/** A fresh copy of the template: one per HTTP test file or scenario. */
export async function createDatabaseFromTemplate(template: string): Promise<TestDatabase> {
  const name = `nuddle_test_${randomUUID().replaceAll("-", "")}`;
  const admin = await connectAdmin();
  await admin.unsafe(`create database ${name} template ${template}`);
  return open(name, admin);
}
