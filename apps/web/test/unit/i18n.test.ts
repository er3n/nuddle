// Web i18n (open-questions#14): the dictionaries stay in step and the invite page picks a language.
import { describe, expect, it } from "vitest";
import { negotiateLocale } from "../../src/i18n/locales";
import { messages } from "../../src/i18n/messages";

/** Every leaf of a dictionary as `path: value`, with functions called once as a sample. */
function leaves(value: unknown, path = ""): [string, string][] {
  if (typeof value === "string") return [[path, value]];
  if (typeof value === "function") return [[`${path}()`, (value as (n: number) => string)(2)]];
  if (Array.isArray(value)) return value.flatMap((v, i) => leaves(v, `${path}[${String(i)}]`));
  if (typeof value === "object" && value !== null) {
    return Object.entries(value).flatMap(([k, v]) => leaves(v, path ? `${path}.${k}` : k));
  }
  throw new Error(`unexpected ${typeof value} at ${path}`);
}

describe("web dictionaries", () => {
  it("English has exactly the entries Turkish (the source) has", () => {
    const paths = (m: unknown) => leaves(m).map(([p]) => p);
    expect(paths(messages.en)).toEqual(paths(messages.tr));
  });

  it.each(["en", "tr"] as const)("%s has no empty text and no em dash", (locale) => {
    for (const [path, text] of leaves(messages[locale])) {
      expect(text.trim(), path).not.toBe("");
      expect(text, path).not.toContain("—");
    }
  });

  it("counts pokes in words that agree with the number", () => {
    expect(messages.en.hero.pokedYou(1)).toBe("This page has poked you 1 time. So far.");
    expect(messages.en.hero.pokedYou(0)).toBe("This page has poked you 0 times. So far.");
    expect(messages.tr.hero.pokedYou(3)).toBe("Bu sayfa seni 3 kez dürttü. Şimdilik.");
  });
});

describe("negotiateLocale", () => {
  it("takes the supported language the browser prefers most", () => {
    expect(negotiateLocale("tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")).toBe("tr");
    expect(negotiateLocale("en-GB,en;q=0.9,tr;q=0.8")).toBe("en");
    expect(negotiateLocale("de-DE,de;q=0.9,tr;q=0.5,en;q=0.4")).toBe("tr");
    expect(negotiateLocale("en;q=0.2, TR;q=0.6")).toBe("tr");
  });

  it("falls back to English when nothing it speaks is asked for", () => {
    expect(negotiateLocale(null)).toBe("en");
    expect(negotiateLocale("")).toBe("en");
    expect(negotiateLocale("de,fr;q=0.8")).toBe("en");
    expect(negotiateLocale("tr;q=0")).toBe("en");
  });
});
