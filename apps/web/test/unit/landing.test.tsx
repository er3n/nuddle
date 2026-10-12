// The landing page (docs/ux/screens.md, Landing) rendered to HTML in each language.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { messages } from "../../src/i18n/messages";
import { Invite } from "../../src/landing/Invite";
import { Landing, StoreButton } from "../../src/landing/Landing";
import { landingMetadata } from "../../src/landing/metadata";
import { nextLevel } from "../../src/landing/PokeDemo";

const text = (html: string) =>
  html
    .replace(/<[^>]+>/g, " ")
    .replace(/&#x27;/g, "'")
    .replace(/\s+/g, " ");

describe("Landing", () => {
  it("is Turkish and branded dürt at /tr, with a link to the English page", () => {
    const html = renderToStaticMarkup(<Landing locale="tr" />);
    expect(text(html)).toContain("Evde biri hep unutuyor. Artık unutamayacak.");
    expect(text(html)).toContain("dürt .");
    expect(html).toContain('href="/" hrefLang="en" lang="en"');
    expect(text(html)).toContain("Bu sayfa seni 0 kez dürttü. Şimdilik.");
  });

  it("is English and branded Nuddle at /, with a link to the Turkish page", () => {
    const html = renderToStaticMarkup(<Landing locale="en" />);
    expect(text(html)).toContain("Someone at home always forgets. Now they can't.");
    expect(text(html)).toContain("nuddle .");
    expect(html).toContain('href="/tr" hrefLang="tr" lang="tr"');
    expect(text(html)).not.toMatch(/Evde|dürt/);
  });

  it("draws the story with words in the page's language", () => {
    expect(renderToStaticMarkup(<Landing locale="en" />)).toContain(">Buy bread</text>");
    expect(renderToStaticMarkup(<Landing locale="tr" />)).toContain(">Ekmek al</text>");
  });

  it("shows no price for Premium until one is decided (open-questions#10)", () => {
    expect(text(renderToStaticMarkup(<Landing locale="tr" />))).toContain(
      "Premium Unutkanlar için Yakında",
    );
  });

  it("says the app is coming instead of linking to a store listing that does not exist yet", () => {
    const html = renderToStaticMarkup(<Landing locale="tr" />);
    expect(text(html)).toContain("Yakında Google Play'de");
    expect(html).not.toContain("play.google.com");
  });
});

describe("StoreButton", () => {
  it("links to the listing once there is one", () => {
    const url = "https://play.google.com/store/apps/details?id=app.nuddle";
    const html = renderToStaticMarkup(<StoreButton url={url} download={messages.en.download} />);
    expect(html).toContain(`href="${url.replaceAll("&", "&amp;")}"`);
    expect(text(html)).toContain("Get it on Google Play");
  });
});

describe("the poke levels toy", () => {
  it("lights one more level per tap and starts over after the fifth", () => {
    const seen = [0];
    for (let i = 0; i < 6; i++) seen.push(nextLevel(seen.at(-1) ?? 0));
    expect(seen).toEqual([0, 1, 2, 3, 4, 5, 0]);
  });

  it("has a button label for every level reached", () => {
    for (const m of Object.values(messages)) {
      expect(m.levels.button).toHaveLength(m.levels.items.length + 1);
    }
  });
});

describe("landingMetadata", () => {
  it("links each language version to the other for search engines", () => {
    const tr = landingMetadata("tr");
    expect(tr.alternates?.canonical).toBe("/tr");
    expect(tr.alternates?.languages).toEqual({ en: "/", tr: "/tr", "x-default": "/" });
    expect(landingMetadata("en").title).toBe(messages.en.meta.title);
  });
});

describe("Invite", () => {
  it("tells the invited person what to do, in their language", () => {
    expect(text(renderToStaticMarkup(<Invite locale="tr" />))).toContain(
      "Seni dürt'e davet ettiler.",
    );
    const en = renderToStaticMarkup(<Invite locale="en" />);
    expect(text(en)).toContain("You've been invited to Nuddle.");
    expect(en).toContain('href="/"');
  });
});
