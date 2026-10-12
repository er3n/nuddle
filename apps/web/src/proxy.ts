// Sends a visitor who opens `/` to their own language (open-questions#14). The language switch
// links to `?lang=xx`: that choice is kept in a cookie and wins over the browser's languages.
import { NextResponse, type NextRequest } from "next/server";
import { isLocale, landingPath, LOCALE_COOKIE, rootLocale } from "./i18n/locales";

const ONE_YEAR_SECONDS = 365 * 24 * 60 * 60;

export function proxy(request: NextRequest): NextResponse {
  const url = request.nextUrl.clone();
  const picked = url.searchParams.get("lang");
  if (picked !== null && isLocale(picked)) {
    url.search = "";
    url.pathname = landingPath[picked];
    const response = NextResponse.redirect(url);
    response.cookies.set(LOCALE_COOKIE, picked, {
      path: "/",
      maxAge: ONE_YEAR_SECONDS,
      sameSite: "lax",
    });
    return response;
  }
  if (url.pathname === landingPath.en) {
    const locale = rootLocale(
      request.cookies.get(LOCALE_COOKIE)?.value,
      request.headers.get("accept-language"),
    );
    if (landingPath[locale] !== url.pathname) {
      url.pathname = landingPath[locale];
      return NextResponse.redirect(url);
    }
  }
  return NextResponse.next();
}

export const config = { matcher: ["/", "/tr"] };
