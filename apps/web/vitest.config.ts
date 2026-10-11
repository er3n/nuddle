import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    projects: [
      {
        test: {
          name: "unit",
          include: ["test/unit/**/*.test.ts"],
        },
      },
      {
        // Hono app.request() against a fresh copy of the migrated template database.
        test: {
          name: "http",
          include: ["test/http/**/*.test.ts"],
          globalSetup: ["test/support/global-setup.ts"],
          testTimeout: 30_000,
          hookTimeout: 60_000,
        },
      },
      {
        // Simulated phones against the HTTP API and Postgres (docs/testing.md, Scenario tests).
        test: {
          name: "scenario",
          include: ["test/scenario/**/*.test.ts"],
          globalSetup: ["test/support/global-setup.ts"],
          testTimeout: 60_000,
          hookTimeout: 60_000,
        },
      },
      {
        test: {
          name: "integration",
          include: ["test/integration/**/*.test.ts"],
          // Each file creates its own database; keep connection count low on the local stack.
          fileParallelism: false,
          testTimeout: 30_000,
          hookTimeout: 60_000,
        },
      },
      {
        // LLM golden set against the real provider. Only `mise run eval`, never CI.
        test: {
          name: "eval",
          include: ["test/eval/**/*.eval.ts"],
          testTimeout: 30 * 60_000,
        },
      },
    ],
  },
});
