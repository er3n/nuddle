import { headers } from "next/headers";
import { negotiateLocale, type Locale } from "./locales";

/** The locale for a page without one in its path, from the browser's languages. */
export async function requestLocale(): Promise<Locale> {
  return negotiateLocale((await headers()).get("accept-language"));
}
