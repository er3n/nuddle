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
        // In-memory server and simulated phones (docs/testing.md, Scenario tests).
        test: {
          name: "scenario",
          include: ["test/scenario/**/*.test.ts"],
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
