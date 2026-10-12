// Web locales (open-questions#14): English Nuddle at `/`, Turkish dürt at `/tr`. A visitor who
// opens `/` is sent to their own language (src/proxy.ts).

export const locales = ["en", "tr"] as const;

export type Locale = (typeof locales)[number];

const defaultLocale: Locale = "en";

export const landingPath: Record<Locale, string> = { en: "/", tr: "/tr" };

/** Remembers the language a visitor picked with the language switch. */
export const LOCALE_COOKIE = "locale";

export function isLocale(value: string): value is Locale {
  return (locales as readonly string[]).includes(value);
}

/**
 * Picks the locale for a page that has no locale in its path (the invite link): the supported
 * language with the highest `q` in `Accept-Language`, English when none matches.
 */
export function negotiateLocale(acceptLanguage: string | null): Locale {
  let best: { locale: Locale; q: number } | undefined;
  for (const part of (acceptLanguage ?? "").split(",")) {
    const [tag = "", ...params] = part.trim().split(";");
    const language = tag.split("-")[0]?.toLowerCase() ?? "";
    if (!isLocale(language)) continue;
    const qParam = params.map((p) => p.trim()).find((p) => p.startsWith("q="));
    const q = qParam === undefined ? 1 : Number(qParam.slice(2));
    if (Number.isNaN(q) || q <= 0) continue;
    if (best === undefined || q > best.q) best = { locale: language, q };
  }
  return best?.locale ?? defaultLocale;
}

/** The language for `/`: the one the visitor picked before, else the browser's. */
export function rootLocale(picked: string | undefined, acceptLanguage: string | null): Locale {
  return picked !== undefined && isLocale(picked) ? picked : negotiateLocale(acceptLanguage);
}
