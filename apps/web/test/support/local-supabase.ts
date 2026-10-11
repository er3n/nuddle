// URLs and keys of the running local Supabase stack, read from `supabase status` (local defaults,
// not secrets). Integration tests that need Auth or Storage use these.
import { execFileSync } from "node:child_process";

export function localSupabase(): { url: string; secretKey: string } {
  const out = execFileSync("supabase", ["status", "-o", "env"], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
  });
  const env = new Map<string, string>();
  for (const line of out.split("\n")) {
    const m = /^([A-Z_]+)="?(.*?)"?$/.exec(line);
    if (m?.[1] !== undefined && m[2] !== undefined) env.set(m[1], m[2]);
  }
  const url = env.get("API_URL");
  const secretKey = env.get("SECRET_KEY");
  if (url === undefined || secretKey === undefined) {
    throw new Error("local Supabase is not running (`supabase start`)");
  }
  return { url, secretKey };
}
