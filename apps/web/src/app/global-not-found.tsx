// 404 for any URL no route matches. The app has one root layout per language, so this page brings
// its own <html> (next.config.ts, experimental.globalNotFound). Its language comes from the browser.
import type { Metadata } from "next";
import { requestLocale } from "../i18n/request";
import { landingPath } from "../i18n/locales";
import { messages } from "../i18n/messages";
import { Document } from "../landing/Document";
import { Logo } from "../landing/Landing";
import styles from "../landing/landing.module.css";

export const metadata: Metadata = { title: "404", robots: { index: false } };

export default async function GlobalNotFound() {
  const locale = await requestLocale();
  const m = messages[locale];
  return (
    <Document locale={locale}>
      <main className={styles.page}>
        <div className={styles.centered}>
          <Logo brand={m.brand} href={landingPath[locale]} />
          <h1 className={styles.centeredTitle}>{m.notFound.title}</h1>
          <a href={landingPath[locale]} className={styles.textLink}>
            {m.notFound.home}
          </a>
        </div>
      </main>
    </Document>
  );
}
