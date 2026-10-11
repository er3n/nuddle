// The deny-all safety net (ADR 0002) on the local Supabase database that `supabase db reset`
// built: every public table has RLS on and the PostgREST roles have no privileges. Supabase's
// default grants exist only there, not in a per-run test database.
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type postgres from "postgres";
import { connectAdmin } from "./db";

describe("deny-all safety net", () => {
  let sql: postgres.Sql;

  beforeAll(async () => {
    sql = await connectAdmin();
  });

  afterAll(async () => {
    await sql.end();
  });

  it("has the schema applied (run `supabase db reset` after adding a migration)", async () => {
    const rows =
      await sql`select 1 from pg_tables where schemaname = 'public' and tablename = 'tasks'`;
    expect(rows).toHaveLength(1);
  });

  it("enables RLS on every public table", async () => {
    const rows = await sql<{ relname: string }[]>`
      select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity
      order by c.relname`;
    expect(rows.map((r) => r.relname)).toEqual([]);
  });

  it("grants nothing on public tables, sequences or functions to anon and authenticated", async () => {
    const rows = await sql<{ grantee: string; object: string; privilege: string }[]>`
      select grantee, table_name as object, privilege_type as privilege
        from information_schema.role_table_grants
        where table_schema = 'public' and grantee in ('anon', 'authenticated')
      union all
      select grantee, object_name, privilege_type
        from information_schema.usage_privileges
        where object_schema = 'public' and object_type = 'SEQUENCE' and grantee in ('anon', 'authenticated')
      union all
      select grantee, routine_name, privilege_type
        from information_schema.routine_privileges
        where routine_schema = 'public' and grantee in ('anon', 'authenticated')
      order by 1, 2, 3`;
    expect(rows).toEqual([]);
  });

  it("revokes the default grants so future tables start with no grants", async () => {
    const rows = await sql<{ acl: string }[]>`
      select d.defaclacl::text as acl from pg_default_acl d
      join pg_namespace n on n.oid = d.defaclnamespace
      join pg_roles r on r.oid = d.defaclrole
      where n.nspname = 'public' and r.rolname = 'postgres'`;
    for (const { acl } of rows) {
      expect(acl).not.toMatch(/\b(anon|authenticated)=/);
    }
  });
});
