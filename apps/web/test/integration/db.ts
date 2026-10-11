// Integration test database (docs/testing.md, Server integration). Each run gets its own database
// `nuddle_test_<id>` in the local Supabase Postgres, migrated from supabase/migrations/, and
// dropped afterwards. Needs `supabase start` (or `mise run dev`).
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

  const sql = postgres(withDatabase(localDatabaseUrl, name), { onnotice: () => undefined });
  const drop = async () => {
    await sql.end();
    await admin.unsafe(`drop database if exists ${name} with (force)`);
    await admin.end();
  };
  try {
    await applyMigrations(sql);
  } catch (error) {
    await drop();
    throw error;
  }
  return { db: drizzle({ client: sql, schema }), sql, drop };
}
