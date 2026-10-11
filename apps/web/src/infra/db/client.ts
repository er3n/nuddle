// Database connection for the API (Drizzle over postgres.js). The API connects as the table owner;
// PostgREST roles get nothing (ADR 0002, deny-all migration).
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";
import type { Db } from "./store";

export function connectDb(
  url: string,
  options: { max?: number } = {},
): {
  db: Db;
  close: () => Promise<void>;
} {
  // `prepare: false` works behind Supabase's transaction pooler as well as a direct connection.
  const client = postgres(url, {
    max: options.max ?? 5,
    prepare: false,
    onnotice: () => undefined,
  });
  return { db: drizzle({ client, schema }), close: () => client.end() };
}
