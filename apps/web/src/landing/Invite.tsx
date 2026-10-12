// An invite link opened where the app is not installed (open-questions#13). The invite does not
// depend on this page: it waits for whoever signs up with the invited number (docs/product.md,
// Connections).
import { landingPath, type Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";
import { Logo, StoreButton } from "./Landing";
import styles from "./landing.module.css";
import { PLAY_STORE_URL } from "./store";

export function Invite({ locale }: { locale: Locale }) {
  const m = messages[locale];
  return (
    <main className={styles.page}>
      <div className={styles.centered}>
        <Logo brand={m.brand} href={landingPath[locale]} />
        <h1 className={styles.centeredTitle}>{m.invite.title}</h1>
        <p className={styles.centeredBody}>{m.invite.body}</p>
        <StoreButton url={PLAY_STORE_URL} download={m.download} />
        <a href={landingPath[locale]} className={styles.textLink}>
          {m.invite.more}
        </a>
      </div>
    </main>
  );
}
