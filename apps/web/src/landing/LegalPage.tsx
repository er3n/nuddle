// The privacy notice and the account deletion page (NUD-85). Play links to both; the text lives in
// i18n/messages/legal.*.ts.
import type { ReactNode } from "react";
import { landingPath, legalPath, type LegalPageName, type Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";
import { Logo } from "./Landing";
import styles from "./landing.module.css";

// An email address or a page on this site written inside the text.
const LINK = /([\w.+-]+@[\w-]+\.[\w.]+\w|nuddle\.app\/[\w/-]*\w)/g;

/** Turns the addresses and site paths a paragraph mentions into links. */
export function linked(text: string): ReactNode[] {
  return text.split(LINK).map((part, i) => {
    if (i % 2 === 0) return part;
    const href = part.includes("@") ? `mailto:${part}` : part.slice("nuddle.app".length);
    return (
      <a key={i} href={href} className={styles.textLink}>
        {part}
      </a>
    );
  });
}

export function LegalPage({ page, locale }: { page: LegalPageName; locale: Locale }) {
  const m = messages[locale];
  const p = m.legal[page];
  const other: Locale = locale === "tr" ? "en" : "tr";
  return (
    <main className={styles.page}>
      <div className={styles.legal}>
        <nav className={styles.legalNav}>
          <Logo brand={m.brand} href={landingPath[locale]} />
          <a
            href={legalPath[page][other]}
            hrefLang={other}
            lang={other}
            className={styles.textLink}
          >
            {m.legal.otherLanguage}
          </a>
        </nav>
        <h1 className={styles.centeredTitle}>{p.title}</h1>
        <p className={styles.legalUpdated}>{p.updated}</p>
        <p className={styles.legalText}>{linked(p.intro)}</p>
        {p.sections.map((s) => (
          <section key={s.heading}>
            <h2 className={styles.legalHeading}>{s.heading}</h2>
            {s.paragraphs.map((text) => (
              <p key={text} className={styles.legalText}>
                {linked(text)}
              </p>
            ))}
            {s.items && (
              <ul className={styles.legalList}>
                {s.items.map((item) => (
                  <li key={item}>{linked(item)}</li>
                ))}
              </ul>
            )}
          </section>
        ))}
        <a href={landingPath[locale]} className={styles.textLink}>
          {m.legal.home}
        </a>
      </div>
    </main>
  );
}
