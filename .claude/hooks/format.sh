#!/usr/bin/env bash
# PostToolUse hook: format the file Claude just wrote or edited (docs/quality.md, Claude Code guardrails).
# Input is the hook JSON on stdin; the path is .tool_input.file_path (absolute).
# Exit 2 sends stderr back to Claude (the edit already happened); exit 0 is silent.
set -euo pipefail

file=$(jq -r '.tool_input.file_path // empty')
[[ -n "$file" && -f "$file" ]] || exit 0

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

case "$file" in
  *.ts | *.tsx | *.mts | *.cts | *.js | *.mjs | *.cjs | *.json | *.yaml | *.yml | *.css)
    # Prettier skips files listed in .prettierignore.
    if ! out=$(mise exec -- pnpm exec prettier --write --ignore-unknown --log-level=warn "$file" 2>&1); then
      echo "prettier failed on $file:" >&2
      echo "$out" >&2
      exit 2
    fi
    ;;
  *.kt | *.kts)
    # ktlint via Spotless is wired in stage 5, when apps/android exists.
    ;;
esac
exit 0
