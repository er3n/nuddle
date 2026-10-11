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
  // `prepare: false` works behind Supabase's transaction pooler (production, port 6543) as well as
  // a direct connection. Idle connections close after 20 s so suspended serverless instances do
  // not hold pooler slots; a pooler that does not answer fails the request after 10 s.
  const client = postgres(url, {
    max: options.max ?? 5,
    prepare: false,
    idle_timeout: 20,
    connect_timeout: 10,
    onnotice: () => undefined,
  });
  return { db: drizzle({ client, schema }), close: () => client.end() };
}
