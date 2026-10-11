// Next mounts the Hono API under /api (ADR 0003). The app is built on the first request so
// `next build` does not need the runtime environment.
import type { App } from "../../../api/app";
import { createProductionApp } from "../../../api/production";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

let app: Promise<App> | undefined;

function handler(request: Request): Promise<Response> {
  app ??= createProductionApp();
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
