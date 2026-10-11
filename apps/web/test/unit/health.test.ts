// A deployment without a database serves only health (docs/architecture.md, Environments):
// healthy on a preview, unhealthy in production.
import { describe, expect, it } from "vitest";
import { createHealthOnlyApp } from "../../src/api/app";
import { deploymentVersion } from "../../src/api/health";

describe("health without a database", () => {
  it("is healthy on a preview, where no database is expected", async () => {
    const app = createHealthOnlyApp({ version: "abc1234", databaseRequired: false });
    const r = await app.request("/api/v1/health");
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({
      status: "ok",
      version: "abc1234",
      database: "not_configured",
    });
  });

  it("is unhealthy in production", async () => {
    const app = createHealthOnlyApp({ version: "abc1234", databaseRequired: true });
    const r = await app.request("/api/v1/health");
    expect(r.status).toBe(503);
    expect(await r.json()).toMatchObject({ status: "error", database: "not_configured" });
  });

  it("serves no other route", async () => {
    const app = createHealthOnlyApp({ version: "abc1234", databaseRequired: false });
    for (const path of ["/api/v1/sync", "/api/internal/tick", "/api/test/reset"]) {
      const r = await app.request(path, { method: path === "/api/v1/sync" ? "GET" : "POST" });
      expect(r.status).toBe(404);
      expect(await r.json()).toMatchObject({ error: { code: "DRT-1004" } });
    }
  });
});

describe("deploymentVersion", () => {
  it("is the short commit on Vercel and dev elsewhere", () => {
    expect(deploymentVersion({ VERCEL_GIT_COMMIT_SHA: "14c0f9e2b7a1d3e5" })).toBe("14c0f9e");
    expect(deploymentVersion({})).toBe("dev");
    expect(deploymentVersion({ VERCEL_GIT_COMMIT_SHA: "" })).toBe("dev");
  });
});
