import type { Locale } from "../i18n/locales";

/**
 * The Instagram account of each language's brand (NUD-149). null where the account has no posts yet:
 * @nuddle.app exists but stays empty until the global launch.
 */
export const instagramUrl: Record<Locale, string | null> = {
  tr: "https://www.instagram.com/durt.app/",
  en: null,
};
