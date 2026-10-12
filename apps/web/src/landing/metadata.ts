import type { Metadata } from "next";
import { landingPath, locales, type Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";

const SITE = "https://nuddle.app";

/** Title, description and the hreflang links between the language versions. */
export function landingMetadata(locale: Locale): Metadata {
  const m = messages[locale];
  return {
    metadataBase: new URL(SITE),
    title: m.meta.title,
    description: m.meta.description,
    alternates: {
      canonical: landingPath[locale],
      languages: {
        ...Object.fromEntries(locales.map((l) => [l, landingPath[l]])),
        "x-default": landingPath.en,
      },
    },
    openGraph: { title: m.meta.title, description: m.meta.description, locale, siteName: m.brand },
  };
}
