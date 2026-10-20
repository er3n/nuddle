// `mise run announce -- announcements/<slug>.md --to <+90...,+90...|*> [--prod] [--yes]` (ADR 0025):
// sends a message from Nuddle to the given numbers, or to everyone with `*`. Without --yes it only
// checks the file and its images and prints who would get it. Local by default; --prod targets
// nuddle.app with ~/.nuddle/prod/TICK_SECRET, and only the human runs that (CLAUDE.md rule 6).
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { basename, resolve } from "node:path";
import {
  announcementProblems,
  imagePaths,
  parseAnnouncementFile,
} from "../src/domain/announcements";
import type { AnnounceResult } from "../src/api/services";

const USAGE =
  "Usage: mise run announce -- announcements/<slug>.md --to <+905xxxxxxxxx,...|*> [--prod] [--yes]";

const args = process.argv.slice(2);
const file = args.find((a, i) => !a.startsWith("--") && args[i - 1] !== "--to");
const toArg = args[args.indexOf("--to") + 1];
const prod = args.includes("--prod");
const confirm = args.includes("--yes");
if (file === undefined || !args.includes("--to") || toArg === undefined) {
  console.error(USAGE);
  process.exit(2);
}
const to = toArg === "*" ? "*" : toArg.split(",").map((p) => p.trim());
if (to !== "*" && to.some((p) => !/^\+\d{8,15}$/.test(p))) {
  console.error("--to takes E.164 numbers separated by commas, or *");
  process.exit(2);
}

// pnpm runs this inside apps/web; the path is relative to where mise was called.
const path = resolve(process.env.INIT_CWD ?? process.cwd(), file);
const announcement = parseAnnouncementFile(basename(path, ".md"), await readFile(path, "utf8"));
const problems = announcementProblems(announcement);
if (problems.length > 0) {
  console.error(`${file}:\n  ${problems.join("\n  ")}`);
  process.exit(1);
}

const base = prod ? "https://nuddle.app" : "http://localhost:3000";

// The phone loads images from the API host: they must be deployed before anyone gets the text.
const missing: string[] = [];
for (const image of imagePaths(announcement.body)) {
  const res = await fetch(`${base}${image}`, { method: "HEAD" });
  if (!res.ok) missing.push(`${image} (${String(res.status)})`);
}
if (missing.length > 0) {
  console.error(`${base} does not serve these images yet:\n  ${missing.join("\n  ")}`);
  process.exit(1);
}

const found = prod
  ? (await readFile(`${homedir()}/.nuddle/prod/TICK_SECRET`, "utf8")).trim()
  : process.env.TICK_SECRET;
if (!found) throw new Error("No TICK_SECRET: set it in .env.local, or use --prod");
const secret: string = found;

async function call(confirmed: boolean): Promise<AnnounceResult> {
  const res = await fetch(`${base}/api/internal/announce`, {
    method: "POST",
    headers: { authorization: `Bearer ${secret}`, "content-type": "application/json" },
    body: JSON.stringify({ announcement, to, confirm: confirmed }),
  });
  if (!res.ok) throw new Error(`${String(res.status)} ${await res.text()}`);
  return (await res.json()) as AnnounceResult;
}

const preview = await call(false);
console.info(`${base}: "${announcement.title}" (${announcement.slug})`);
if (preview.unknownPhones.length > 0) {
  console.info(`  no account for: ${preview.unknownPhones.join(", ")}`);
}
console.info(`  new recipients, with a notification: ${String(preview.newRecipients)}`);
console.info(
  `  already have it: ${String(preview.alreadySent)}${preview.textChanged ? " (they get the new text, no notification)" : ""}`,
);
if (!confirm) {
  console.info("Run again with --yes to send it.");
  process.exit(0);
}
const done = await call(true);
console.info(done.sent ? `Sent to ${String(done.newRecipients)} new people.` : "Nothing sent.");
