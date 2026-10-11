// Vercel "Ignored Build Step" (vercel.json, docs/bootstrap-plan.md Stage 7): exit 0 skips the
// deployment, exit 1 builds. A change confined to apps/android/ is skipped; anything else, or any
// doubt, builds. It runs before dependencies are installed, so it uses Node built-ins only and
// runs as TypeScript through Node's type stripping.
import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

/** Paths are relative to the repository root, as `git diff --name-only` prints them. */
export function shouldBuild(changedFiles: readonly string[]): boolean {
  return changedFiles.length === 0 || changedFiles.some((f) => !f.startsWith("apps/android/"));
}

function changedSince(base: string): string[] {
  const out = execFileSync("git", ["diff", "--name-only", base, "HEAD"], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  return out.split("\n").filter((line) => line !== "");
}

function main(): number {
  // The commit of the branch's last successful deployment; unset on its first deployment.
  const previous = process.env.VERCEL_GIT_PREVIOUS_SHA ?? "";
  for (const base of previous === "" ? ["HEAD^"] : [previous, "HEAD^"]) {
    try {
      const files = changedSince(base);
      const build = shouldBuild(files);
      console.log(
        `${build ? "Building" : "Skipping"}: ${String(files.length)} file(s) changed since ${base}`,
      );
      return build ? 1 : 0;
    } catch {
      // Not in the shallow clone; try the next base.
    }
  }
  console.log("Building: could not compute the changed files");
  return 1;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) process.exit(main());
