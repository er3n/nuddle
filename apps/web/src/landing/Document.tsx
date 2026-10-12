// The <html> shell every page shares: language, fonts and the token colors.
import type { ReactNode } from "react";
import type { Locale } from "../i18n/locales";
import { fontVariables } from "./fonts";
import styles from "./landing.module.css";
import { paletteVars } from "./theme";

export function Document({ locale, children }: { locale: Locale; children: ReactNode }) {
  return (
    <html lang={locale} className={fontVariables}>
      <body className={styles.body} style={paletteVars}>
        {children}
      </body>
    </html>
  );
}
