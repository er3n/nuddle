import type { Metadata } from "next";
import { landingPath, legalPath, locales, type LegalPageName, type Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";
import { POSTER_SIZE, promoVideo } from "./promo";
import { SITE } from "./site";

/** Title, description, the hreflang links between the language versions and the share preview. */
export function landingMetadata(locale: Locale): Metadata {
  const m = messages[locale];
  const image = { url: promoVideo[locale].poster, ...POSTER_SIZE, alt: m.video.posterAlt };
  return {
    metadataBase: new URL(SITE),
    title: m.meta.title,
    description: m.meta.description,
    applicationName: m.meta.appName,
    alternates: {
      canonical: landingPath[locale],
      languages: {
        ...Object.fromEntries(locales.map((l) => [l, landingPath[l]])),
        "x-default": landingPath.en,
      },
    },
    robots: { index: true, follow: true },
    openGraph: {
      type: "website",
      url: landingPath[locale],
      title: m.meta.title,
      description: m.meta.description,
      siteName: m.meta.appName,
      locale: m.meta.ogLocale,
      alternateLocale: locales.filter((l) => l !== locale).map((l) => messages[l].meta.ogLocale),
      images: [image],
    },
    twitter: {
      card: "summary_large_image",
      title: m.meta.title,
      description: m.meta.description,
      images: [image],
    },
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
