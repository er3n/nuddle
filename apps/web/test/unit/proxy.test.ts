// `/` opens in the visitor's language; a language picked with the switch wins (open-questions#14).
import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { rootLocale } from "../../src/i18n/locales";
import { proxy } from "../../src/proxy";

function open(path: string, headers: Record<string, string> = {}) {
  return proxy(new NextRequest(`https://nuddle.app${path}`, { headers }));
}

const TURKISH_BROWSER = { "accept-language": "tr-TR,tr;q=0.9,en;q=0.8" };

describe("rootLocale", () => {
  it("prefers the picked language, then the browser's", () => {
    expect(rootLocale("en", "tr-TR")).toBe("en");
    expect(rootLocale(undefined, "tr-TR")).toBe("tr");
    expect(rootLocale("xx", "tr-TR")).toBe("tr");
    expect(rootLocale(undefined, null)).toBe("en");
  });
});

describe("proxy", () => {
  it("sends a Turkish browser from / to /tr", () => {
    const response = open("/", TURKISH_BROWSER);
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("https://nuddle.app/tr");
  });

  it("serves / as it is to an English or unknown browser", () => {
    expect(open("/", { "accept-language": "en-US" }).headers.get("location")).toBeNull();
    expect(open("/").headers.get("location")).toBeNull();
  });

  it("keeps a Turkish browser on / once it picked English with the switch", () => {
    const picked = open("/?lang=en", TURKISH_BROWSER);
    expect(picked.headers.get("location")).toBe("https://nuddle.app/");
    expect(picked.cookies.get("locale")?.value).toBe("en");

    const later = open("/", { ...TURKISH_BROWSER, cookie: "locale=en" });
    expect(later.headers.get("location")).toBeNull();
  });

  it("remembers Turkish picked with the switch, even in an English browser", () => {
    const picked = open("/tr?lang=tr", { "accept-language": "en-US" });
    expect(picked.headers.get("location")).toBe("https://nuddle.app/tr");
    expect(picked.cookies.get("locale")?.value).toBe("tr");

    expect(
      open("/", { "accept-language": "en-US", cookie: "locale=tr" }).headers.get("location"),
    ).toBe("https://nuddle.app/tr");
  });

  it("never moves a visitor away from /tr they opened on purpose", () => {
    expect(open("/tr", { "accept-language": "en-US" }).headers.get("location")).toBeNull();
  });

  it("ignores a language it does not have", () => {
    expect(open("/?lang=de").headers.get("location")).toBeNull();
  });
});
