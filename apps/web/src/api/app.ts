// The Hono app (ADR 0003): request id, Supabase JWT verification, DRT error mapping, the public
// routes, the internal endpoints and, only in E2E mode, the test endpoints. Without a database
// (preview) only health is served.
import { OpenAPIHono, z } from "@hono/zod-openapi";
import { requestId } from "hono/request-id";
import { databaseAnswers } from "../infra/db/store";
import type { AppDeps } from "./deps";
import { ApiError, errorBody } from "./errors";
import { registerHealth } from "./health";
import { internalRoutes } from "./internal";
import { registerRoutes, type AppEnv } from "./routes";

export type App = OpenAPIHono<AppEnv>;

/** Request id, DRT error mapping and the 404 shape shared by every app variant. */
function baseApp(): App {
  const app = new OpenAPIHono<AppEnv>({
    defaultHook: (result, c) => {
      if (!result.success) {
        return c.json(
          errorBody("validation_failed", z.prettifyError(result.error), c.get("requestId")),
          400,
        );
      }
      return undefined;
    },
  });

  app.use(requestId());

  app.onError((err, c) => {
    const id = c.get("requestId");
    if (err instanceof ApiError)
      return c.json(errorBody(err.name, err.message, id, err.details), err.status);
    console.error(`request ${id} failed`, err);
    return c.json(errorBody("internal", "internal error", id), 500);
  });
  app.notFound((c) => c.json(errorBody("not_found", "no such route", c.get("requestId")), 404));
  return app;
}

const invitePreviewPath = /^\/api\/v1\/invites\/[^/]+\/preview$/;

export async function createApp(deps: AppDeps): Promise<App> {
  const app = baseApp();

  // Every /api/v1 route except sign-in, health and the invite preview (ADR 0016) needs a Supabase
  // access token.
  app.use("/api/v1/*", async (c, next) => {
    if (
      c.req.path.startsWith("/api/v1/auth/") ||
      c.req.path === "/api/v1/health" ||
      invitePreviewPath.test(c.req.path)
    ) {
      return next();
    }
    const header = c.req.header("authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice("Bearer ".length) : "";
    try {
      c.set("caller", await deps.tokens.verify(token));
    } catch {
      throw new ApiError("unauthorized", "missing or invalid access token");
    }
    return next();
  });

  app.openAPIRegistry.registerComponent("securitySchemes", "bearer", {
    type: "http",
    scheme: "bearer",
    bearerFormat: "JWT",
    description: "Supabase Auth access token from /api/v1/auth/verify",
  });

  const api = new OpenAPIHono<AppEnv>({ defaultHook: app.defaultHook });
  registerHealth(api, {
    version: () => deps.version,
    database: async () => ((await databaseAnswers(deps.db, 2000)) ? "ok" : "down"),
    databaseRequired: true,
  });
  registerRoutes(api, deps);
  app.route("/api", api);
  app.route("/api/internal", internalRoutes(deps));

  if (deps.e2e) {
    // Loaded only in E2E mode (docs/architecture.md, E2E mode).
    const { e2eRoutes } = await import("./e2e");
    app.route("/api/test", e2eRoutes(deps, deps.e2e));
  }
  return app;
}

/**
 * A deployment without a database (Vercel preview, docs/architecture.md Environments): only
 * health, which reports the database as not configured. Every other route answers 404.
 */
export function createHealthOnlyApp(options: { version: string; databaseRequired: boolean }): App {
  const app = baseApp();
  const api = new OpenAPIHono<AppEnv>({ defaultHook: app.defaultHook });
  registerHealth(api, {
    version: () => options.version,
    database: () => Promise.resolve("not_configured"),
    databaseRequired: options.databaseRequired,
  });
  app.route("/api", api);
  return app;
}

/** The OpenAPI document of the public API (ADR 0008). */
export function openApiDocument(app: App): ReturnType<App["getOpenAPIDocument"]> {
  return app.getOpenAPIDocument({
    openapi: "3.0.3",
    info: {
      title: "Nuddle API",
      version: "1",
      description:
        "Commands and sync for the Nuddle app (ADR 0006). Generated from zod schemas; do not edit by hand.",
      license: {
        name: "All rights reserved",
        url: "https://github.com/er3n/nuddle/blob/main/LICENSE",
      },
    },
    // The app sets the host per build flavor (local: adb reverse tcp:3000 tcp:3000).
    servers: [{ url: "/" }],
  });
}
