# announcements

Messages from Nuddle to people (ADR 0025). One file per announcement, `<slug>.md`; the slug is the file name.

```markdown
---
title: Premium geldi
summary: SMS ve sesli arama ile dürtme artık Premium'da.
button:            # optional
  label: Premium'a bak
  target: https://nuddle.app/premium   # or an app route: invite, profile, inbox
---

Body in CommonMark: headings, **bold**, *italic*, lists, [links](https://nuddle.app).

![Premium](/announcements/premium/hero.png)
```

- Images go in `apps/web/public/announcements/<slug>/` and are referenced by that root-relative path. Deploy the web app before sending to production: the dry run checks that every image answers.
- Send: `mise run announce -- announcements/<slug>.md --to +905xxxxxxxxx` (dry run), then the same with `--yes`. `--to` takes numbers separated by commas, or `*` for everyone signed up now. `--prod` is the human's.
- Sending the same file again updates the text for everyone who has it (no second notification) and reaches new numbers.
- No em dash anywhere (CLAUDE.md rule 7).
