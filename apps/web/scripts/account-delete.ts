// `mise run account:delete -- <+90 phone> [--prod] [--yes]` (NUD-88): deletes an account asked for
// by email without the app (nuddle.app/delete-account), through the same service as in-app
// deletion. Without --yes it only prints what would be deleted. Local by default; --prod targets
// nuddle.app with ~/.nuddle/prod/TICK_SECRET, only when the human asks (CLAUDE.md rule 6).
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
const phone = args.find((a) => !a.startsWith("--"));
const prod = args.includes("--prod");
const confirm = args.includes("--yes");
if (phone === undefined || !/^\+\d{8,15}$/.test(phone)) {
  console.error("Usage: mise run account:delete -- +905xxxxxxxxx [--prod] [--yes]");
  process.exit(2);
}

const base = prod ? "https://nuddle.app" : "http://localhost:3000";
const found = prod
  ? (await readFile(join(homedir(), ".nuddle/prod/TICK_SECRET"), "utf8")).trim()
  : process.env.TICK_SECRET;
if (!found) throw new Error("No TICK_SECRET: set it in .env.local, or use --prod");
const secret: string = found;

async function call(confirmed: boolean): Promise<{ account: unknown; deleted: boolean }> {
  const res = await fetch(`${base}/api/internal/delete-account`, {
    method: "POST",
    headers: { authorization: `Bearer ${secret}`, "content-type": "application/json" },
    body: JSON.stringify({ phone, confirm: confirmed }),
  });
  if (!res.ok) throw new Error(`${String(res.status)} ${await res.text()}`);
  return (await res.json()) as { account: unknown; deleted: boolean };
}

const preview = await call(false);
if (preview.account === null) {
  console.info(`${base}: no account with ${phone}. Nothing to delete.`);
  process.exit(0);
}
console.info(`${base}: this account would be deleted:`);
console.info(JSON.stringify(preview.account, null, 2));
if (!confirm) {
  console.info("Run again with --yes to delete it.");
  process.exit(0);
}
const done = await call(true);
console.info(
  done.deleted ? "Deleted. The rest goes with account_purge in a week." : "Nothing deleted.",
);
