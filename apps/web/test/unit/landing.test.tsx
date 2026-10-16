// The landing page (docs/ux/screens.md, Landing) rendered to HTML in each language.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { messages } from "../../src/i18n/messages";
import { Invite } from "../../src/landing/Invite";
import { Landing, StoreButton } from "../../src/landing/Landing";
import { LegalPage, linked } from "../../src/landing/LegalPage";
import { landingMetadata, legalMetadata } from "../../src/landing/metadata";
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
    expect(html).toContain('href="/?lang=en" hrefLang="en" lang="en"');
    expect(text(html)).toContain("Bu sayfa seni 0 kez dürttü. Şimdilik.");
  });

  it("is English and branded Nuddle at /, with a link to the Turkish page", () => {
    const html = renderToStaticMarkup(<Landing locale="en" />);
    expect(text(html)).toContain("Someone at home always forgets. Now they can't.");
    expect(text(html)).toContain("nuddle .");
    expect(html).toContain('href="/tr?lang=tr" hrefLang="tr" lang="tr"');
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

  it("says a photo may come from the gallery (open-questions#48)", () => {
    const tr = text(renderToStaticMarkup(<Landing locale="tr" />));
    expect(tr).toContain("galeriden seçilir");
    expect(tr).not.toMatch(/yüklenemez|Kamera sadece/);
    const en = text(renderToStaticMarkup(<Landing locale="en" />));
    expect(en).toContain("picked from the gallery");
    expect(en).not.toMatch(/no old photos|camera only opens/);
  });

  it("links to the privacy notice and the account deletion page in its own language", () => {
    const tr = renderToStaticMarkup(<Landing locale="tr" />);
    expect(tr).toContain('href="/tr/privacy"');
    expect(tr).toContain('href="/tr/delete-account"');
    const en = renderToStaticMarkup(<Landing locale="en" />);
    expect(en).toContain('href="/privacy"');
    expect(en).toContain('href="/delete-account"');
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

describe("LegalPage", () => {
  it("names the cross-border transfer, photo retention and account deletion (NUD-85)", () => {
    for (const locale of ["tr", "en"] as const) {
      const page = text(renderToStaticMarkup(<LegalPage page="privacy" locale={locale} />));
      for (const provider of ["Supabase", "Vercel", "Firebase", "Twilio", "OpenRouter"]) {
        expect(page).toContain(provider);
      }
      expect(page).toContain("Frankfurt");
      expect(page).toContain(
        locale === "tr" ? "görev kapanınca silinir" : "deleted when the task closes",
      );
    }
  });

  it("gives a way to ask for deletion without the app", () => {
    const tr = renderToStaticMarkup(<LegalPage page="deleteAccount" locale="tr" />);
    expect(text(tr)).toContain("Uygulama olmadan");
    expect(tr).toContain('href="mailto:privacy@nuddle.app"');
    const en = renderToStaticMarkup(<LegalPage page="deleteAccount" locale="en" />);
    expect(text(en)).toContain("Without the app");
    expect(en).toContain('href="/tr/delete-account" hrefLang="tr" lang="tr"');
  });

  it("links the deletion page from the privacy notice in the same language", () => {
    expect(renderToStaticMarkup(<LegalPage page="privacy" locale="tr" />)).toContain(
      'href="/tr/delete-account"',
    );
    expect(renderToStaticMarkup(<LegalPage page="privacy" locale="en" />)).toContain(
      'href="/delete-account"',
    );
  });

  it("turns addresses and site paths in the text into links and leaves the rest", () => {
    const html = renderToStaticMarkup(
      <p>{linked("Yaz: a.b@nuddle.app. Ya da nuddle.app/tr/privacy, nuddle.app sadece.")}</p>,
    );
    expect(html).toContain('<a href="mailto:a.b@nuddle.app"');
    expect(html).toContain('<a href="/tr/privacy"');
    expect(html).toContain("</a>. Ya da");
    expect(html).toContain("</a>, nuddle.app sadece.");
  });

  it("links each language version to the other for search engines", () => {
    const m = legalMetadata("deleteAccount", "tr");
    expect(m.title).toBe("Hesabını sil · dürt");
    expect(m.alternates?.canonical).toBe("/tr/delete-account");
    expect(m.alternates?.languages).toEqual({
      en: "/delete-account",
      tr: "/tr/delete-account",
      "x-default": "/delete-account",
    });
  });
});
