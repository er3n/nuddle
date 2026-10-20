// Messages from Nuddle (ADR 0025): stored by `mise run announce`, a sync hint to each new
// recipient, the content through sync. Sending again updates the text without a second hint.
import { describe, expect, it } from "vitest";
import type { AnnouncementContent } from "../../src/domain/announcements";
import { aPerson } from "../builders";
import { at, sibelAndEren } from "./fixtures";

const premium: AnnouncementContent = {
  slug: "premium",
  title: "Premium geldi",
  summary: "SMS ile dürtme artık Premium'da.",
  body: "## Neler var?\n\n![Premium](/announcements/premium/hero.png)",
  button: { label: "Premium'a bak", target: "https://nuddle.app/premium" },
};

const hints = (pushes: { event: string }[]) => pushes.filter((p) => p.event === "announcement");

describe("scenarios: announcements", () => {
  it("reaches only the numbers given, through a hint and the next sync", async () => {
    const { world, sibel, eren } = await sibelAndEren();

    const result = await world.announce(premium, [eren]);

    expect(result).toMatchObject({ sent: true, newRecipients: 1, alreadySent: 0, problems: [] });
    expect(hints(world.push.to("eren"))).toEqual([
      expect.objectContaining({ hint: "sync", taskId: null }),
    ]);
    expect(hints(world.push.to("sibel"))).toEqual([]);
    expect([...eren.phone.announcements.values()]).toEqual([
      { title: premium.title, summary: premium.summary, body: premium.body },
    ]);
    expect(sibel.phone.announcements.size).toBe(0);
  });

  it("a dry run stores and sends nothing and names unknown numbers", async () => {
    const { world, eren } = await sibelAndEren();
    const stranger = aPerson("Defne").phone;

    const preview = await world.announce(premium, "*", false);
    expect(preview).toMatchObject({ sent: false, newRecipients: 2, unknownPhones: [] });

    const one = await world.announce(premium, [eren], false);
    expect(one.sent).toBe(false);
    expect(hints(world.push.to("eren"))).toEqual([]);
    expect(eren.phone.announcements.size).toBe(0);

    const withStranger = await world.announce(premium, [eren, stranger], false);
    expect(withStranger.unknownPhones).toEqual([stranger]);
    expect(withStranger.newRecipients).toBe(1);
  });

  it("* reaches everyone; sending again only reaches new people and updates the text", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    await world.announce(premium, [eren]);

    const again = await world.announce({ ...premium, summary: "Yeni metin" }, "*");

    expect(again).toMatchObject({ newRecipients: 1, alreadySent: 1, textChanged: true });
    expect(hints(world.push.to("eren"))).toHaveLength(1);
    expect(hints(world.push.to("sibel"))).toHaveLength(1);
    // Eren's phone gets the new text on its next sync, without a second hint.
    await eren.phone.sync();
    expect([...eren.phone.announcements.values()].map((a) => a.summary)).toEqual(["Yeni metin"]);
    expect([...sibel.phone.announcements.values()].map((a) => a.summary)).toEqual(["Yeni metin"]);
  });

  it("an unchanged repeat sends nothing and moves no cursor", async () => {
    const { world, eren } = await sibelAndEren();
    await world.announce(premium, [eren]);
    const syncs = eren.phone.announcementSyncs.length;

    const again = await world.announce(premium, [eren]);
    await eren.phone.sync();

    expect(again).toMatchObject({ newRecipients: 0, alreadySent: 1, textChanged: false });
    expect(hints(world.push.to("eren"))).toHaveLength(1);
    expect(eren.phone.announcementSyncs).toHaveLength(syncs);
  });

  it("refuses a file with problems and sends nothing", async () => {
    const { world, eren } = await sibelAndEren();
    const result = await world.announce(
      {
        ...premium,
        body: "![x](https://elsewhere.example/x.png)",
        button: { label: "Git", target: "settings" },
      },
      [eren],
    );
    expect(result.sent).toBe(false);
    expect(result.problems).toHaveLength(2);
    expect(hints(world.push.to("eren"))).toEqual([]);
  });

  it("a full sync after sign-in brings the announcements the person already got", async () => {
    const { world, eren } = await sibelAndEren();
    await world.announce(premium, [eren]);

    const full = await eren.phone.fullSync();

    expect(full.announcements?.map((a) => a.title)).toEqual([premium.title]);
  });

  it("goes with a deleted account's purge", async () => {
    const { world, eren } = await sibelAndEren();
    await world.announce(premium, [eren]);
    expect(await world.rowsHolding(eren)).toMatchObject({ announcementRecipients: 1 });

    await eren.deleteAccount();
    await world.advanceTo(at("2026-10-19T08:32:00"));

    expect(await world.rowsHolding(eren)).toEqual({});
  });
});
