import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Agent instructions live in the root CLAUDE.md; do not generate apps/web/AGENTS.md.
  agentRules: false,
};

export default nextConfig;
