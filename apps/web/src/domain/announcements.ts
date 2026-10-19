// Messages from Nuddle (ADR 0025): a markdown file with frontmatter, sent by `mise run announce`.
import { parse as parseYaml } from "yaml";
import type { PersonId, PushMessage } from "./types";

interface AnnouncementButton {
  label: string;
  /** An app route the phone knows, or an https URL opened in the browser. */
  target: string;
}

export interface AnnouncementContent {
  slug: string;
  title: string;
  /** The notification text and the bell line. */
  summary: string;
  /** CommonMark. */
  body: string;
  button: AnnouncementButton | null;
}

/** Routes an announcement's button may open in the app (docs/ux/platform.md, Navigation). */
const BUTTON_ROUTES = ["invite", "profile", "inbox"] as const;

export const SLUG = /^[a-z0-9][a-z0-9-]{0,63}$/;

const TITLE_MAX = 80;
const SUMMARY_MAX = 200;

/** Every image path in the body: `![alt](path)` and `<img src="path">`. */
export function imagePaths(body: string): string[] {
  const md = [...body.matchAll(/!\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g)].map(
    (m) => m[1] ?? "",
  );
  const html = [...body.matchAll(/<img\s[^>]*src=["']([^"']+)["']/gi)].map((m) => m[1] ?? "");
  return [...md, ...html];
}

/** What is wrong with an announcement; empty when it can be sent. */
export function announcementProblems(a: AnnouncementContent): string[] {
  const problems: string[] = [];
  if (!SLUG.test(a.slug)) problems.push(`slug "${a.slug}" must be lowercase letters, digits and -`);
  if (a.title.trim() === "") problems.push("title is empty");
  if (a.title.length > TITLE_MAX) problems.push(`title is longer than ${String(TITLE_MAX)}`);
  if (a.summary.trim() === "") problems.push("summary is empty");
  if (a.summary.length > SUMMARY_MAX)
    problems.push(`summary is longer than ${String(SUMMARY_MAX)}`);
  if (a.body.trim() === "") problems.push("body is empty");
  for (const text of [a.title, a.summary, a.body, a.button?.label ?? ""]) {
    if (text.includes("—")) {
      problems.push("an em dash is not allowed (CLAUDE.md rule 7)");
      break;
    }
  }
  const prefix = `/announcements/${a.slug}/`;
  for (const path of imagePaths(a.body)) {
    if (!path.startsWith(prefix)) problems.push(`image ${path} must be under ${prefix}`);
  }
  if (a.button !== null) {
    if (a.button.label.trim() === "") problems.push("button label is empty");
    const t = a.button.target;
    if (!t.startsWith("https://") && !(BUTTON_ROUTES as readonly string[]).includes(t)) {
      problems.push(`button target ${t} must be https:// or one of ${BUTTON_ROUTES.join(", ")}`);
    }
  }
  return problems;
}

/**
 * Reads `announcements/<slug>.md`: YAML frontmatter between `---` lines (`title`, `summary`,
 * optional `button: { label, target }`), then the markdown body.
 */
export function parseAnnouncementFile(slug: string, text: string): AnnouncementContent {
  const m = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/.exec(text);
  if (!m) throw new Error("the file must start with frontmatter between --- lines");
  const front: unknown = parseYaml(m[1] ?? "");
  if (typeof front !== "object" || front === null) throw new Error("frontmatter is not a map");
  const f = front as Record<string, unknown>;
  const str = (v: unknown, name: string): string => {
    if (typeof v !== "string") throw new Error(`${name} must be a string`);
    return v.trim();
  };
  let button: AnnouncementButton | null = null;
  if (f.button !== undefined && f.button !== null) {
    if (typeof f.button !== "object") throw new Error("button must be a map of label and target");
    const b = f.button as Record<string, unknown>;
    button = { label: str(b.label, "button.label"), target: str(b.target, "button.target") };
  }
  return {
    slug,
    title: str(f.title, "title"),
    summary: str(f.summary, "summary"),
    body: (m[2] ?? "").trim(),
    button,
  };
}

/** A sync hint to each new recipient; the notification comes from the synced row. */
export function announcementPushes(to: readonly PersonId[]): PushMessage[] {
  return to.map((p) => ({ to: p, hint: "sync", taskId: null, event: "announcement" }));
}
