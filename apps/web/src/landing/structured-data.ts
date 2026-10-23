// schema.org data for search engines: the site, the Android app and the FAQ of one landing page.
import { landingPath, type Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";
import { promoVideo } from "./promo";
import { SITE } from "./site";
import { PLAY_STORE_URL } from "./store";

export function landingJsonLd(locale: Locale) {
  const m = messages[locale];
  const url = new URL(landingPath[locale], SITE).href;
  const image = new URL(promoVideo[locale].poster, SITE).href;
  return {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "WebSite",
        "@id": `${url}#website`,
        url,
        name: m.meta.appName,
        inLanguage: locale,
      },
      {
        "@type": "MobileApplication",
        "@id": `${url}#app`,
        name: m.meta.appName,
        url,
        description: m.meta.description,
        image,
        inLanguage: locale,
        operatingSystem: "Android",
        applicationCategory: "LifestyleApplication",
        offers: { "@type": "Offer", price: "0", priceCurrency: m.meta.currency },
        ...(PLAY_STORE_URL === null ? {} : { installUrl: PLAY_STORE_URL }),
      },
      {
        "@type": "FAQPage",
        "@id": `${url}#faq`,
        inLanguage: locale,
        mainEntity: m.faq.items.map((item) => ({
          "@type": "Question",
          name: item.question,
          acceptedAnswer: { "@type": "Answer", text: item.answer },
        })),
      },
    ],
  };
}
