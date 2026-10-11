// drizzle-kit writes migrations into supabase/migrations/ with timestamp names, so the Supabase
// CLI applies them together with the hand-written SQL in the same folder (ADR 0002).
import { defineConfig } from "drizzle-kit";

export default defineConfig({
  dialect: "postgresql",
  schema: "./src/infra/db/schema.ts",
  out: "../../supabase/migrations",
  migrations: { prefix: "supabase" },
});
