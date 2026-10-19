// Announcement files (ADR 0025): frontmatter, problems that stop a send, image paths.
import { describe, expect, it } from "vitest";
import {
  announcementProblems,
  announcementPushes,
  imagePaths,
  parseAnnouncementFile,
} from "../../src/domain/announcements";

const FILE = `---
title: Premium geldi
summary: SMS ile dürtme artık Premium'da.
button:
  label: Premium'a bak
  target: https://nuddle.app/premium
---

## Neler var?

![Premium](/announcements/premium/hero.png)
<img src="/announcements/premium/second.png" alt="">
`;

describe("announcement files", () => {
  it("reads the frontmatter and the body", () => {
    expect(parseAnnouncementFile("premium", FILE)).toEqual({
      slug: "premium",
      title: "Premium geldi",
      summary: "SMS ile dürtme artık Premium'da.",
      body: '## Neler var?\n\n![Premium](/announcements/premium/hero.png)\n<img src="/announcements/premium/second.png" alt="">',
      button: { label: "Premium'a bak", target: "https://nuddle.app/premium" },
    });
  });

  it("the button is optional", () => {
    const a = parseAnnouncementFile("x", "---\ntitle: T\nsummary: S\n---\nBody");
    expect(a.button).toBeNull();
    expect(announcementProblems(a)).toEqual([]);
  });

  it("a file without frontmatter or with a missing field is an error", () => {
    expect(() => parseAnnouncementFile("x", "Body only")).toThrow(/frontmatter/);
    expect(() => parseAnnouncementFile("x", "---\ntitle: T\n---\nBody")).toThrow(/summary/);
  });

  it("finds markdown and html images", () => {
    expect(imagePaths(parseAnnouncementFile("premium", FILE).body)).toEqual([
      "/announcements/premium/hero.png",
      "/announcements/premium/second.png",
    ]);
  });

  it("a valid file has no problems", () => {
    expect(announcementProblems(parseAnnouncementFile("premium", FILE))).toEqual([]);
  });

  it("names every problem: foreign images, unknown routes, em dashes, empty and long fields", () => {
    const a = parseAnnouncementFile("premium", FILE);
    expect(
      announcementProblems({
        ...a,
        slug: "Premium!",
        title: "x".repeat(81),
        summary: "",
        body: "Bir — iki ![x](https://elsewhere.example/x.png)",
        button: { label: "Git", target: "settings" },
      }),
    ).toEqual([
      'slug "Premium!" must be lowercase letters, digits and -',
      "title is longer than 80",
      "summary is empty",
      "an em dash is not allowed (CLAUDE.md rule 7)",
      "image https://elsewhere.example/x.png must be under /announcements/Premium!/",
      "button target settings must be https:// or one of invite, profile, inbox",
    ]);
  });

  it("each new recipient gets a sync hint without a task", () => {
    expect(announcementPushes(["a", "b"])).toEqual([
      { to: "a", hint: "sync", taskId: null, event: "announcement" },
      { to: "b", hint: "sync", taskId: null, event: "announcement" },
    ]);
  });
});
