// Architecture rules for apps/web (docs/architecture.md, Layering rules; docs/quality.md).
const path = require("node:path");

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: "domain-is-pure",
      comment:
        "src/domain is pure TypeScript: no infra, api, ORM, HTTP framework or anything doing I/O. It receives `now` and data and returns decisions.",
      severity: "error",
      from: { path: "^apps/web/src/domain/" },
      to: {
        path: [
          "^apps/web/src/(infra|api|app)/",
          "(^|/)node_modules/(drizzle-orm|drizzle-kit|hono|@hono|pg|postgres|@supabase|firebase-admin|next|undici|axios|ky)(/|$)",
        ],
      },
    },
    {
      name: "domain-no-node-io",
      comment:
        "src/domain may not use Node built-ins that do I/O or read time and randomness from the host.",
      severity: "error",
      from: { path: "^apps/web/src/domain/" },
      to: {
        dependencyTypes: ["core"],
        path: "^(node:)?(fs|fs/promises|net|http|https|http2|dns|child_process|worker_threads|cluster|dgram|tls|os|process|perf_hooks|timers|crypto)$",
      },
    },
    {
      name: "no-circular",
      severity: "error",
      from: {},
      to: { circular: true },
    },
    {
      name: "not-to-unresolvable",
      severity: "error",
      from: {},
      to: { couldNotResolve: true },
    },
  ],
  options: {
    doNotFollow: { path: "node_modules" },
    exclude: { path: "(^|/)\\.next/" },
    tsPreCompilationDeps: true,
    // Absolute: TypeScript 6 finds no inputs when the config file name is relative.
    tsConfig: { fileName: path.join(__dirname, "apps/web/tsconfig.json") },
    enhancedResolveOptions: {
      exportsFields: ["exports"],
      conditionNames: ["import", "require", "node", "default", "types"],
    },
  },
};
