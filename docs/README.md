# Docs map

| File | What it holds |
|---|---|
| `product.md` | What we build and why, MVP scope, what is out |
| `glossary.md` | Domain terms in English and Turkish UI, banned words, voice and copy rules |
| `domain.md` | Entities, task states and transitions, time rules, poke levels and plan, device execution, reachability |
| `architecture.md` | Stack, repo layout, layering, sync model, timers, E2E mode, environments |
| `adr/` | One file per architecture decision, with rejected alternatives |
| `testing.md` | Test layers, scenario tests, UI and E2E strategy, commands |
| `quality.md` | Lint and static analysis rules, Claude Code guardrails |
| `ux/screens.md` | Every screen: route, purpose, exact Turkish copy, notes; status to UI mapping |
| `ux/components.md` | The Compose component set with signatures and specs |
| `ux/tokens.json` | Colors, type, spacing, radii, motion |
| `ux/platform.md` | Android permissions, alarms, channels, missed-ring detection, navigation, accessibility |
| `ux/screens/` | PNG render of every canvas board (390 dp wide at 2x) |
| `ux/source/` | The original design HTML for each board plus `canvas.json`, for exact values |
| `bootstrap-plan.md` | Seven stages from empty repo to beta skeleton, then feature order |
| `human-checklist.md` | Accounts, secrets, production steps and device checks only a human can do |
| `open-questions.md` | Undecided items with the default to use meanwhile |

Tickets live in the Nuddle Linear workspace. They point to these docs; they never replace them.

Origins: product decisions from the 2026-10-10 decision summary, UI from the "Dürt Mobil UX" canvas and the developer handoff doc (2026-10-10), architecture and testing decisions from the 2026-10-11 discussion. Where the handoff and later decisions differ (R2 vs Supabase Storage, separate repos vs monorepo), the later decision is what these docs say.
