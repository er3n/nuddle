# Nuddle (dürt)

A reminder app where someone says a task once and the person who forgets is poked until it is done. Android first (Kotlin + Compose), API on Vercel (Next.js + Hono), Supabase (Postgres, Auth, Storage). One-person team; most code is written by agents. Global brand Nuddle, Turkish brand "dürt" (Play store names in `docs/product.md`), `applicationId` `com.dokukoza.nuddle` in production (ADR 0028; Kotlin namespace and debug builds stay `app.nuddle`).

## Source of truth

The docs in `docs/` decide. Code follows them.

- If a doc and the code disagree, the doc wins. If you need to change a decision, change the doc first (for architecture: a new ADR), then the code.
- If a task would force you to contradict a doc, **stop and ask**. Do not silently pick.
- Undecided things are in `docs/open-questions.md`. Use the marked default, leave `TODO(open-questions#N)`, mention it in the PR.
- Rejected options are listed in the ADRs. Do not re-propose them without new information.

## What to read for which task

| You are working on | Read first |
|---|---|
| Anything, first time in this repo | `docs/product.md`, `docs/architecture.md` |
| Task states, timing, pokes, sync, server logic | `docs/domain.md`, ADRs 0005, 0006, 0007, 0009 |
| A screen or component | `docs/ux/screens.md` (the screen's entry + PNG), `docs/ux/components.md`, `docs/ux/tokens.json`, `docs/glossary.md` |
| Alarms, notifications, permissions, deep links | `docs/ux/platform.md`, ADR 0005 |
| UI copy or any user-facing string | `docs/glossary.md` |
| Tests | `docs/testing.md` |
| Lint, structure, tooling | `docs/quality.md`, ADR 0001 |
| The API contract | ADR 0008 |
| Setting up the repo or a stage | `docs/bootstrap-plan.md` |

## Commands

Use only these. They work the same for you, the human and CI.

| Command | Use |
|---|---|
| `mise run dev` | Start local Supabase, apply migrations and seed, start the web app |
| `mise run fix` | Run all formatters and autofixers |
| `mise run lint` | All lint and static checks |
| `mise run test:fast` | Fast loop after every change (target under a minute) |
| `mise run test` | Full suite before a PR |
| `mise run e2e:smoke` | Two-emulator smoke set before merging |
| `mise run tick` | Run server timers once locally |
| `mise run android:bundle` | Signed prod release bundle for Play (needs the upload key on the machine) |
| `mise run android:publish` | Build, sign and upload the prod bundle to Play internal testing (ADR 0026); raise `versionCode` first |
| `mise run contract:gen` | Regenerate `packages/contract/openapi.yaml` |
| `mise run eval` | LLM golden sets: extraction and poke plans (needs an API key) |
| `mise run stories` | 100 everyday sentences through the real extractor and planner, for judging plans (needs an API key) |
| `mise run account:delete -- <phone> [--yes]` | Delete an account by phone for an email request; dry run without `--yes`. `--prod` only when the human asks (rule 6) |
| `mise run announce -- <file> --to <phones\|*> [--yes]` | Send a message from Nuddle (ADR 0025); dry run without `--yes`. `--prod` only when the human asks (rule 6) |
| `mise run golden:export` | Production task stories with their LLM calls, read-only, to git-ignored `golden/` |

**Long-running commands** (Gradle builds, `test`, any E2E, emulator boot) must run in the background with a timeout. Do not block waiting on them. Poll the log file and keep working or report. Anything that may take more than 1-2 minutes counts as long.

## Hard rules

1. **Never read the wall clock in domain code.** Inject `Clock` / `now` (ADR 0009). Lint enforces it.
2. **Never edit a test to make it pass.** If behavior changed on purpose, say so in the PR.
3. **Every behavior change comes with a test** at the lowest layer that catches it. Scenario tests for anything that spans sender and recipient.
4. **Android talks only to our API.** No direct Supabase table access from the app (ADR 0002).
5. **Everything is code.** No dashboard-only configuration in Supabase, Vercel or Firebase. Migrations, cron jobs, auth hooks and grants live in `supabase/migrations/`.
6. **Production only when the human asks.** The agent changes production (Vercel env and deploys, the Supabase cloud project, Play Console, Firebase) when the human asks for it in the conversation, and reports what it changed. Two things stay off even then: force pushes, and a bare `supabase db push` or `supabase config push` (they would push the local test numbers, ADR 0022); cloud migrations go through `mise exec -- supabase/cloud/configure.sh migrate`. Until the beta (no users yet, decided 2026-10-11, NUD-33) the agent also applies merged migrations right after the merge without being asked, and may delete cloud data when a migration needs it. Changed 2026-10-23 at the human's request (NUD-144); before that production was the human's alone.
7. **Strings live in resources.** No literals in `Text()`. Turkish is the source language. No em dash (U+2014) anywhere: UI copy, docs, code comments.
8. **Screens are stateless.** A screen composable takes `UiState` and callbacks, never a ViewModel. Every component and screen state has a `@Preview`.
9. **No hex values or magic numbers in screen code.** Everything comes from the theme built from `docs/ux/tokens.json`.
10. **No baselines, no suppressions without a reason.** `eslint-disable` and `@Suppress` need a written justification.
11. **The repo is public. Secrets never go in it.** Use `.env.local` (git-ignored), GitHub Actions secrets and Vercel/Supabase env. gitleaks runs on commit. `google-services.json` is not committed. Test OTP numbers exist only in the local Supabase config, except the one Play review number, which lives in `~/.nuddle/prod` and never in the repo (ADR 0022).

## Tickets (Linear)

Work is tracked in the **Nuddle** Linear workspace, reached through the Linear MCP server.

- An issue is a thin pointer: which doc section to follow and the "Done when" criteria. The docs are the spec. If an issue and a doc disagree, the doc wins and you say so in the issue.
- Before starting: read the issue, move it to In Progress, and use its identifier in the branch name, e.g. `nud-12-stage-1-repo-foundation` (lowercase identifier, then a short slug).
- Put the identifier in the PR title, e.g. `NUD-12 Stage 1: repo foundation`, so Linear links the PR.
- When the PR is open: comment on the issue with the PR link, any open-question defaults used, and anything you could not verify. Move it to In Review. The human merges; Linear closes the issue.
- Work you discover that is out of scope becomes a new issue in Backlog with a one-line pointer to the relevant doc. Do not expand the current PR.
- Open decisions stay in `docs/open-questions.md`, not in Linear.
- If the Linear MCP server is not available, continue from `docs/bootstrap-plan.md` and say so in the PR.

## Workflow

- Work on a branch, open a PR, keep PRs small and focused on one stage step or one screen.
- Before opening a PR: `mise run fix`, `mise run lint`, `mise run test`. For UI or poke behavior also `mise run e2e:smoke`.
- In the PR description: what changed, which docs it follows, any `open-questions` defaults used, and for UI the Roborazzi images next to the design PNG.
- When a doc needs to change, put the doc change in the same PR and call it out.

## Language

Code, comments, docs, commit messages and PRs in English. UI in Turkish (`values/strings.xml`), English strings added before the global launch. In code the domain verb is **poke** (`Poke`, `PokeLevel`, `planPokes`).
