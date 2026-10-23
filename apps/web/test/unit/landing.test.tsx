// The landing page (docs/ux/screens.md, Landing) rendered to HTML in each language.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { messages } from "../../src/i18n/messages";
import { Invite } from "../../src/landing/Invite";
import { Landing, StoreButton } from "../../src/landing/Landing";
import { LegalPage, linked } from "../../src/landing/LegalPage";
import { landingMetadata, legalMetadata } from "../../src/landing/metadata";
import { nextLevel } from "../../src/landing/PokeDemo";
import { embedUrl, promoVideo } from "../../src/landing/PromoVideo";
import { landingJsonLd } from "../../src/landing/structured-data";
import robots from "../../src/app/robots";
import sitemap from "../../src/app/sitemap";

const text = (html: string) =>
  html
    .replace(/<[^>]+>/g, " ")
    .replace(/&#x27;/g, "'")
    .replace(/&quot;/g, '"')
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

  it("spells out the medicine reminder, without medical advice (NUD-106)", () => {
    const tr = text(renderToStaticMarkup(<Landing locale="tr" />));
    expect(tr).toContain("İlaç hatırlatıcı: annen unutmasın.");
    expect(tr).toContain("tıbbi tavsiye vermez");
    const en = text(renderToStaticMarkup(<Landing locale="en" />));
    expect(en).toContain("Medicine reminder: mom won't forget.");
    expect(en).toContain("gives no medical advice");
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

  it("links to the brand's Instagram only where the account has posts (NUD-149)", () => {
    const tr = renderToStaticMarkup(<Landing locale="tr" />);
    expect(tr).toContain('href="https://www.instagram.com/durt.app/"');
    expect(tr).toContain('rel="noopener noreferrer"');
    expect(renderToStaticMarkup(<Landing locale="en" />)).not.toContain("instagram.com");
  });
});

describe("the promo video (NUD-94)", () => {
  it("shows the video of the page's language", () => {
    expect(renderToStaticMarkup(<Landing locale="tr" />)).toContain(
      'src="/promo/durt-promo-tr.jpg"',
    );
    expect(renderToStaticMarkup(<Landing locale="en" />)).toContain(
      'src="/promo/nuddle-promo-en.jpg"',
    );
    expect(promoVideo.tr.youtubeId).not.toBe(promoVideo.en.youtubeId);
  });

  it("loads nothing from YouTube before the play tap", () => {
    for (const locale of ["tr", "en"] as const) {
      const html = renderToStaticMarkup(<Landing locale={locale} />);
      expect(html).not.toMatch(/youtube|ytimg|<iframe/);
      expect(text(html)).toContain(messages[locale].video.note);
    }
  });

  it("plays from YouTube's no-cookie domain", () => {
    expect(embedUrl("nJILmwdDBzc")).toBe(
      "https://www.youtube-nocookie.com/embed/nJILmwdDBzc?autoplay=1&rel=0&playsinline=1",
    );
  });
});

describe("the features since the first landing (NUD-94)", () => {
  it("shows repeating tasks and one-time messages in the app's words (glossary)", () => {
    const tr = text(renderToStaticMarkup(<Landing locale="tr" />));
    expect(tr).toContain("Her gün 09:00");
    expect(tr).toContain("Ertele · 22:37");
    expect(tr).toContain('"Tamam" deyince kapanır');
    expect(tr).not.toMatch(/kanıt|tekrar kuralı|\bseri\b/);
    const en = text(renderToStaticMarkup(<Landing locale="en" />));
    expect(en).toContain("Every day 09:00");
  });

  it("has the same features in every language", () => {
    expect(messages.en.features.items).toHaveLength(messages.tr.features.items.length);
  });
});

describe("what happens while a poke rings (NUD-120 to NUD-138)", () => {
  it("shows the newer poke features in the app's words (glossary)", () => {
    const tr = text(renderToStaticMarkup(<Landing locale="tr" />));
    for (const words of [
      '"Sibel diyor ki: ilacını iç"',
      "Pes etmem",
      "Zaten yaptın mı?",
      "Kaydırarak bitir",
      "Ara · Mesaj at",
      "Ben hallederim",
      "Dürtmem çalmadı",
      "Ekli fotoğraf",
      "Anneme söyle",
    ]) {
      expect(tr).toContain(words);
    }
    expect(tr).not.toMatch(/kanıt|oturum|kayboldu/);
    const en = text(renderToStaticMarkup(<Landing locale="en" />));
    expect(en).toContain("Slide to finish");
    expect(en).not.toMatch(/proof/i);
  });

  it("has the same cards and questions in every language", () => {
    expect(messages.en.ringing.items).toHaveLength(messages.tr.ringing.items.length);
    expect(messages.en.faq.items).toHaveLength(messages.tr.faq.items.length);
    expect(messages.en.pricing.free.features).toHaveLength(
      messages.tr.pricing.free.features.length,
    );
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

  it("gives a share preview with the promo poster in the page's language", () => {
    const tr = landingMetadata("tr");
    expect(tr.openGraph).toMatchObject({
      url: "/tr",
      locale: "tr_TR",
      alternateLocale: ["en_US"],
      siteName: "dürt",
      images: [{ url: "/promo/durt-promo-tr.jpg", width: 1280, height: 720 }],
    });
    expect(tr.twitter).toMatchObject({ card: "summary_large_image" });
  });

  it("keeps the description short enough for a search result", () => {
    for (const m of Object.values(messages))
      expect(m.meta.description.length).toBeLessThanOrEqual(160);
  });
});

describe("structured data", () => {
  it("describes the free Android app and the FAQ of the page", () => {
    const graph = landingJsonLd("tr")["@graph"];
    expect(graph[1]).toMatchObject({
      "@type": "MobileApplication",
      name: "dürt",
      url: "https://nuddle.app/tr",
      operatingSystem: "Android",
      offers: { price: "0", priceCurrency: "TRY" },
    });
    expect(graph[1]).not.toHaveProperty("installUrl");
    expect(graph[2]).toMatchObject({ "@type": "FAQPage" });
    expect(graph[2]?.mainEntity).toHaveLength(messages.tr.faq.items.length);
  });

  it("is on the page as JSON-LD that no text can break out of", () => {
    const html = renderToStaticMarkup(<Landing locale="en" />);
    const json = /<script type="application\/ld\+json">(.*?)<\/script>/.exec(html)?.[1];
    expect(json).toBeDefined();
    expect(JSON.parse(json ?? "")).toEqual(landingJsonLd("en"));
    expect(json).not.toContain("<");
  });
});

describe("robots.txt and the sitemap", () => {
  it("lists every landing and legal page with its other language", () => {
    const urls = sitemap().map((entry) => entry.url);
    expect(urls).toEqual([
      "https://nuddle.app/",
      "https://nuddle.app/tr",
      "https://nuddle.app/privacy",
      "https://nuddle.app/tr/privacy",
      "https://nuddle.app/delete-account",
      "https://nuddle.app/tr/delete-account",
    ]);
    expect(sitemap()[1]?.alternates?.languages).toEqual({
      en: "https://nuddle.app/",
      tr: "https://nuddle.app/tr",
    });
  });

  it("points crawlers to the sitemap and keeps them out of the API", () => {
    expect(robots()).toEqual({
      rules: { userAgent: "*", allow: "/", disallow: "/api/" },
      sitemap: "https://nuddle.app/sitemap.xml",
    });
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
