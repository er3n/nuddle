// Next mounts the Hono API under /api (ADR 0003). The app is built on the first request so
// `next build` does not need the runtime environment. A deployment without DATABASE_URL (Vercel
// preview, docs/architecture.md Environments) serves only /api/v1/health.
import { createHealthOnlyApp, type App } from "../../../api/app";
import { deploymentVersion } from "../../../api/health";
import { createProductionApp } from "../../../api/production";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

let app: Promise<App> | undefined;

function build(env: Record<string, string | undefined>): Promise<App> {
  if ((env.DATABASE_URL ?? "") !== "") return createProductionApp(env);
  return Promise.resolve(
    createHealthOnlyApp({
      version: deploymentVersion(env),
      databaseRequired: env.VERCEL_ENV === "production",
    }),
  );
}

function handler(request: Request): Promise<Response> {
  app ??= build(process.env);
  return app.then((a) => a.fetch(request));
}

export {
  handler as DELETE,
  handler as GET,
  handler as OPTIONS,
  handler as PATCH,
  handler as POST,
  handler as PUT,
};
