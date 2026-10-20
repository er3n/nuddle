# 0025 Announcements are markdown files sent by a CLI and delivered through sync

Status: Accepted (2026-10-19). Decided in NUD-112.

## Context
We want to tell people things that are not about a task: "Premium geldi", a new feature, a change in the rules. The message needs a title, a longer text with formatting and pictures, and sometimes one action ("Premium'a bak"). It has to arrive as a notification, stay findable in the bell, and open a detail screen from either place. Push is only a hint and notifications come from what sync brings (architecture.md, Push); an announcement must not break that.

## Decision
- **Authored as a file.** One markdown file per announcement in `announcements/<slug>.md` with frontmatter: `title`, `summary` (the notification text and the bell line), optional `button` (`label`, `target`). The body is CommonMark. Images live in `apps/web/public/announcements/<slug>/` and are referenced by a root-relative path (`/announcements/<slug>/hero.png`), so they ship with the web app and need no bucket.
- **Sent by a CLI.** `mise run announce -- <file> --to <phones|*> [--yes] [--prod]` reads the file and posts it to `POST /api/internal/announce` (tick secret, like `account:delete`). `--to` takes comma-separated phone numbers, or `*` for everyone signed up at that moment. Without `--yes` it is a dry run: it shows how many people would get it and which phones are unknown. `--prod` is the human's (CLAUDE.md rule 6).
- **Stored on the server.** `announcements(id, slug unique, title, summary, body, button_label, button_target, created_at)` and `announcement_recipients(announcement_id, person_id, change_seq, created_at)`. Sending the same slug again updates the text and adds new recipients; people who already have it get the new text without a second notification.
- **Delivered through sync.** `SyncResponse.announcements` holds the recipient rows whose `change_seq` is past the cursor, each with its content. Every new recipient gets a `sync` hint with `event: "announcement"`.
- **On the phone.** Room keeps the announcements with a local `openedAt`. An announcement that arrives in an incremental sync posts a notification on the channel `news` ("Nuddle'dan haberler", DEFAULT importance); a full sync after sign-in posts nothing, as for tasks. The bell lists announcements, unread ones under "Yeni" and in the badge. The notification and the bell item open `announcement/{id}`, which renders the markdown and the button and marks it opened.
- **Links and images are ours.** The detail loads images only from the app's own API host (root-relative paths are resolved against it). A link in the text or a `https://` button target opens the browser; any other button target is an app route (`invite`, `profile`).
- **Opting out is the system channel.** Turning off `news` stops the notification; the item still shows in the bell. The `news` channel never counts toward reachability or bell warnings.

## Consequences
- An announcement with images needs the web app deployed before it is sent to production; the CLI dry run checks that every image path answers 200.
- Read state lives on the phone: a second phone or a reinstall shows it unread again. Accepted until read state matters (for example for a paid feature).
- People who sign up after `*` was sent do not get it. Sending the same file again with `*` reaches them without notifying the others again.
- Android gains a markdown renderer and an image loader as dependencies.

## Rejected
- **An admin web page with an editor:** needs admin auth and more surface for one sender; a file in git is reviewed, versioned and code (CLAUDE.md rule 5).
- **A notification straight from the FCM payload:** breaks "a hint never shows anything by itself" and leaves nothing in the bell when the push is lost.
- **Images in a public Storage bucket:** an upload step, a bucket and a policy to keep as code, for files that change only with an announcement.
- **Server-side read state:** an endpoint and an outbox command for a badge that is fine per phone today.
- **Using the word "duyuru" in the UI:** the glossary keeps it away from one-time messages; announcements show as "Nuddle'dan".
