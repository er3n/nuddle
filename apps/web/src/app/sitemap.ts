// /sitemap.xml: the landing and legal pages in every language, each with its hreflang siblings.
import type { MetadataRoute } from "next";
import { landingPath, legalPath, locales, type Locale } from "../i18n/locales";
import { SITE } from "../landing/site";

const absolute = (path: string) => new URL(path, SITE).href;

function entries(
  paths: Record<Locale, string>,
  priority: number,
  changeFrequency: "weekly" | "yearly",
): MetadataRoute.Sitemap {
  const languages = Object.fromEntries(locales.map((l) => [l, absolute(paths[l])]));
  return locales.map((locale) => ({
    url: absolute(paths[locale]),
    changeFrequency,
    priority,
    alternates: { languages },
  }));
}

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    ...entries(landingPath, 1, "weekly"),
    ...entries(legalPath.privacy, 0.3, "yearly"),
    ...entries(legalPath.deleteAccount, 0.3, "yearly"),
  ];
}
