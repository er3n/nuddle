# 0021 A repeating task is a series of one-off occurrences

Status: Accepted (2026-10-15). Decided in NUD-77.

## Context
"annem her gün 9'da ilacını alsın", "eren her salı çöpü çıkarsın", "bahar hafta içi her sabah 7'de kalksın": the story set (NUD-70) had five of these and the extractor answered every one with "Tekrarlayan görev henüz yok". A family's reminders are mostly of this shape. Everything the app does (plan, pokes, Bitti, missed, approval, the sender's view) works on one task with one deadline, and the planner learns from how each task ended.

## Decision
- **The text decides.** The server keeps the model's rule only when the sentence itself says the task comes back ("her gün", "her sabah", "her salı", "her hafta", "hafta içi", "iş günü"); the model invented a rule for a bill or a school run said once, and a wrong series is worse than a missing one.
- **A rule, not a calendar.** A repeating task carries a `repeat` rule: `every` is `day`, `week` (with ISO weekdays, 1 Monday to 7 Sunday) or `weekdays`, and `time` is a local clock time. Nothing more often than once a day; "her saat başı", "dakikada bir", "3 kere" and "hem sabah hem akşam" keep getting a reply that says so.
- **One open occurrence at a time.** The extractor returns the rule and the first occurrence's time (the next matching time after now); the first occurrence is created like any task and its id becomes the `series_id` every occurrence shares. Each occurrence is a full task: its own plan, pokes, events and status, so the planner's history and the person profile see "the medicine on Tuesday" as a task of its own.
- **The next occurrence is created when one closes.** Done, a photo approved (also by timeout), missed, dropped ("Bugün olmaz") and "Ben hallettim" all bring the next occurrence at once, at the rule's first time after the closed one's own time (or after now when it closed late). It copies the title, detail, timing, grace, lead and finish mode; `raw_text` stays on the first.
- **A cancel ends the series.** "Görevi iptal et" by the sender cancels the open occurrence and no successor is created. A removed connection or a deleted account ends it the same way. There is no "skip once" for the sender; the recipient's "Bugün olmaz" is the skip.
- **Sync carries the rule** on every occurrence; the app shows it as "Her gün 09:00", "Her salı 19:00", "Hafta içi 07:30" on the row, the card, the detail and the preview, and the cancel sheet says the series ends. The time of the rule follows the first occurrence's time when the sender picks another one in the preview or edits the deadline (the edited occurrence keeps the series; the rule's time is not rewritten by an edit).
- **Storage:** `tasks.repeat` (jsonb) and `tasks.series_id`, indexed. No separate series table: the series is the set of tasks with that id.

## Consequences
- A daily task done at 09:05 shows tomorrow's occurrence on home right away, with "Yarın 09:00". That is the expected shape of a recurring reminder.
- A series lives as long as it is not cancelled. Forgotten series pile up as missed occurrences; the missed card's "Kaldır" and the sender's cancel are the way out. A "pause" is not in this ADR.
- The plan golden set and the stories need no change: the planner sees one task at a time.

## Rejected
- **A series table with generated occurrences ahead of time:** more state to keep in sync (edits, cancels, time zone changes), and the home list would show a week of the same task.
- **A cron-style rule (`RRULE`):** nobody writes "every second Thursday" to a family app; three shapes cover the sentences, and the extractor stays simple.
- **More than once a day:** "sabah ve akşam ilaç" is two series with two sentences; the preview's one question cannot carry two times.
