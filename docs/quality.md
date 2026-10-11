# Lint and code quality

Principle: a rule is either enforced by a machine or it does not exist. A rule written only in a doc will eventually be broken by an agent.
Start strict with zero warnings and **no baseline files**. Tightening later is far more expensive.

## TypeScript (`apps/web`)

- `tsconfig`: `strict`, `noUncheckedIndexedAccess`.
- ESLint flat config with `typescript-eslint` `strictTypeChecked`. Rules that matter most:
  - `@typescript-eslint/no-floating-promises` and `no-misused-promises`: a forgotten `await` on a DB write or push send.
  - `@typescript-eslint/switch-exhaustiveness-check`: the task state machine must handle every status.
  - `eslint-comments/require-description`: any `eslint-disable` must say why.
  - `no-restricted-syntax` in `src/domain`: bans `Date.now()` and argument-less `new Date()`.
- Prettier for formatting.
- `dependency-cruiser`: `src/domain` may not import `src/infra`, `src/api`, `drizzle-orm`, `hono`, or anything doing I/O.
- `knip`: unused files, exports and dependencies fail the lint.

## Kotlin (`apps/android`)

- Compiler: `allWarningsAsErrors = true`.
- ktlint via Spotless for formatting.
- detekt with the Compose rule set (`io.nlopez.compose.rules`) and:
  - `ForbiddenMethodCall`: `System.currentTimeMillis`, `java.time.Instant.now`, `LocalDateTime.now`, `kotlin.time.Clock.System.now` (and the deprecated `kotlinx.datetime.Clock.System.now`) outside the DI module.
- Android Lint with these as errors: `NewApi`, `MissingPermission`, exact alarm permission checks, notification permission checks.
- Konsist architecture tests (run as unit tests):
  - `domain` package does not import `android.*`.
  - Screen composables do not take ViewModels; they take `UiState` and lambdas.
  - No string literals passed to `Text()`; strings come from resources.
  - `Color(...)` is only constructed in the `theme` package.
  - Every public composable in `ui/components` has a `@Preview`.

## Contract and database

- `redocly lint packages/contract/openapi.yaml`: `operationId` on every operation, consistent naming.
- Contract diff check: regenerate and fail if `openapi.yaml` changed.
- `squawk` on new migrations: catches unsafe changes (adding `NOT NULL` without default, locking index builds).

## Repo-wide

| Tool | When | Purpose |
|---|---|---|
| lefthook | pre-commit | format + fast lint on staged files, a few seconds at most |
| gitleaks | pre-commit and CI | catch committed secrets |
| GitHub secret push protection, CodeQL, Dependabot alerts | on push (repo settings) | free on the public repo, second line of defense |
| `mise run fix` | any time | run all formatters and autofixers |
| `mise run lint` | CI, required | everything above |
| Renovate | weekly, grouped | dependency updates as PRs |

Agents run `mise run fix` before `mise run lint`.

## Claude Code guardrails

- Hooks: after file edits, run the formatter for the touched language.
- `settings.json` allow list: `mise run *`, `pnpm *`, `./gradlew *`, `supabase start|stop|status|db reset`, `maestro *`, `adb *`, `git` (non-force).
- Deny or always ask: `supabase db push`, `supabase link` and anything with `--linked` against the cloud project, `vercel --prod`, `vercel promote`, `vercel env rm`, `git push --force`, deleting branches on the remote. Until the beta the agent applies merged migrations only through `supabase/cloud/configure.sh migrate` (CLAUDE.md hard rule 6).

## Deliberately not used

- Coverage percentage gates (agents satisfy them with empty tests).
- SonarQube and complexity metrics (noise for a one-person team).
- Mutation testing for now. Revisit for `planPokes` and the state machine once they settle.
