import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Agent instructions live in the root CLAUDE.md; do not generate apps/web/AGENTS.md.
  agentRules: false,
  experimental: {
    // One root layout per language (open-questions#14) leaves no single layout for a 404.
    globalNotFound: true,
  },
};

export default nextConfig;
