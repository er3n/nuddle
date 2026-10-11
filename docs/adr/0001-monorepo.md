# 0001 Monorepo: pnpm workspaces + Gradle + mise

Status: Accepted (2026-10-11). Supersedes the earlier plan of separate `android` and `web` repos.

## Context
Code is written mostly by agents. A contract change must land in the API and in the Android client in one commit, and one agent must be able to see both sides. One person maintains everything.

## Decision
- One **public** repo `er3n/nuddle` on the founder's personal account (decided 2026-10-11, instead of the `nuddle-app` org). It can be transferred to an org later; GitHub keeps redirects, but Vercel, CI secrets and Renovate must be re-linked after a transfer.
- Public because standard GitHub-hosted runners are free and larger for public repos, and code scanning and secret push protection come at no cost. Consequences of being public are listed below.
- pnpm workspaces for the TypeScript side (`apps/web`, later `packages/*`).
- `apps/android` is an independent Gradle build with its own wrapper. Android Studio opens that folder.
- `mise` pins tool versions (node, pnpm, java, supabase CLI, maestro) and defines the tasks every human, agent and CI job uses: `dev`, `fix`, `lint`, `test:fast`, `test`, `e2e:smoke`, `tick`, `eval`.
- One GitHub Actions workflow runs `mise run lint` and `mise run test`.

## Consequences of a public repo
- Every secret lives outside the repo: `.env.local` (git-ignored), GitHub Actions secrets, Vercel and Supabase env. gitleaks on commit, GitHub secret push protection on.
- `google-services.json` is not committed; CI writes it from a secret. The Firebase Android API key is restricted to the package name and signing certificate in Google Cloud.
- Fixed test OTP codes exist only in the local `supabase/config.toml`. The cloud project never has test numbers enabled, otherwise anyone reading the repo could sign in as them.
- Internal endpoints (`/api/internal/*`) rely on secrets, never on obscurity. E2E endpoints are compiled out of production.
- Workflows from fork PRs need approval and get no secrets (GitHub default; keep it).
- License: see `open-questions.md`.

## Consequences
- `CLAUDE.md` lists only mise tasks. No agent has to know pnpm filters or Gradle task names.
- Vercel uses root directory `apps/web` and an ignored build step so Android-only commits do not deploy.

## Rejected
- **Separate repos** (`android`, `web`): contract changes split across PRs, agents lose half the context.
- **Turborepo:** only understands JS, a single TS app does not need task caching yet. Add later if TS packages grow.
- **Nx with the Gradle plugin:** large configuration surface, agents lose time on it, no payoff for two apps.
- **Bazel / Pants:** far out of proportion.
