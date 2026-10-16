import type { Metadata } from "next";
import { landingPath, legalPath, locales, type LegalPageName, type Locale } from "../i18n/locales";
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

/** Title and the hreflang links of a legal page. */
export function legalMetadata(page: LegalPageName, locale: Locale): Metadata {
  const paths = legalPath[page];
  return {
    metadataBase: new URL(SITE),
    title: messages[locale].legal[page].metaTitle,
    alternates: {
      canonical: paths[locale],
      languages: {
        ...Object.fromEntries(locales.map((l) => [l, paths[l]])),
        "x-default": paths.en,
      },
    },
  };
}
