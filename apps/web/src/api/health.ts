// GET /api/v1/health (docs/bootstrap-plan.md, Stage 7): the deployed version and whether the
// database answers. Public, no token. Preview deployments have no database (docs/architecture.md,
// Environments), so there "not_configured" is healthy; in production it is not.
import { createRoute, type OpenAPIHono, z } from "@hono/zod-openapi";
import type { AppEnv } from "./routes";

const DatabaseState = z.enum(["ok", "down", "not_configured"]);
type DatabaseState = z.infer<typeof DatabaseState>;

const Health = z
  .object({
    status: z.enum(["ok", "error"]),
    version: z.string().openapi({
      example: "14c0f9e",
      description: "Git commit of the deployment, `dev` outside Vercel",
    }),
    database: DatabaseState,
  })
  .openapi("Health");

export interface HealthProbe {
  /** Read per request: building the contract must not touch dependencies. */
  version: () => string;
  database: () => Promise<DatabaseState>;
  /** Without a database the deployment is unhealthy (production) or expected (preview). */
  databaseRequired: boolean;
}

export function registerHealth(app: OpenAPIHono<AppEnv>, probe: HealthProbe): void {
  app.openapi(
    createRoute({
      method: "get",
      path: "/v1/health",
      operationId: "getHealth",
      summary: "Deployment version and database connectivity",
      tags: ["health"],
      security: [],
      responses: {
        200: {
          description: "Healthy",
          content: { "application/json": { schema: Health } },
        },
        503: {
          description: "The database is down, or missing where it is required",
          content: { "application/json": { schema: Health } },
        },
      },
    }),
    async (c) => {
      const database = await probe.database();
      const healthy =
        database === "ok" || (database === "not_configured" && !probe.databaseRequired);
      const body = {
        status: healthy ? "ok" : "error",
        version: probe.version(),
        database,
      } as const;
      return healthy ? c.json(body, 200) : c.json(body, 503);
    },
  );
}

/** The version shown by health: the short Git commit Vercel builds from. */
export function deploymentVersion(env: Record<string, string | undefined>): string {
  const sha = env.VERCEL_GIT_COMMIT_SHA ?? "";
  return sha === "" ? "dev" : sha.slice(0, 7);
}
