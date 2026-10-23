// The Play promo videos (NUD-94). A plain module, so server code can read it too (PromoVideo is a
// client component).
import type { Locale } from "../i18n/locales";

/** The unlisted YouTube uploads of the Play listing videos (tr-TR dürt, en-US Nuddle). */
export const promoVideo: Record<Locale, { youtubeId: string; poster: string }> = {
  tr: { youtubeId: "nJILmwdDBzc", poster: "/promo/durt-promo-tr.jpg" },
  en: { youtubeId: "7NTe4rYgRP4", poster: "/promo/nuddle-promo-en.jpg" },
};

/** The size of both posters, in pixels. */
export const POSTER_SIZE = { width: 1280, height: 720 } as const;
