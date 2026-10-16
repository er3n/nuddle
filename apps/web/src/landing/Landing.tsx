// The landing page (docs/ux/screens.md, Landing; Landing.dc.html). One component for every locale.
import { landingPath, legalPath, locales, type Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";
import type { Messages } from "../i18n/messages/types";
import { cx } from "./cx";
import styles from "./landing.module.css";
import { PokeButton, PokeDemoProvider, PokedBadge, PokeLevels } from "./PokeDemo";
import { Story } from "./Story";
import { PLAY_STORE_URL } from "./store";

export function Logo({ brand, href }: { brand: string; href: string }) {
  return (
    <a href={href} className={styles.logo}>
      {brand}
      <span className={styles.logoDot}>.</span>
    </a>
  );
}

/** The store button, or a "coming soon" label while there is no listing to link to. */
export function StoreButton({
  url,
  download,
}: {
  url: string | null;
  download: Messages["download"];
}) {
  if (url === null)
    return <span className={cx(styles.storeButton, styles.storeSoon)}>{download.storeSoon}</span>;
  return (
    <a href={url} className={styles.storeButton}>
      {download.store}
    </a>
  );
}

function otherLocale(locale: Locale): Locale {
  return locales.find((l) => l !== locale) ?? locale;
}

function Header({ locale, m }: { locale: Locale; m: Messages }) {
  const other = otherLocale(locale);
  return (
    <header className={styles.header}>
      <Logo brand={m.brand} href={landingPath[locale]} />
      <nav aria-label={m.nav.label} className={styles.nav}>
        <a href="#how" className={styles.navLink}>
          {m.nav.how}
        </a>
        <a href="#levels" className={styles.navLink}>
          {m.nav.levels}
        </a>
        <a href="#pricing" className={styles.navLink}>
          {m.nav.pricing}
        </a>
        <a
          href={`${landingPath[other]}?lang=${other}`}
          hrefLang={other}
          lang={other}
          className={styles.navLink}
        >
          {m.nav.otherLanguage}
        </a>
        <a href="#download" className={styles.navCta}>
          {m.nav.download}
        </a>
      </nav>
    </header>
  );
}

function Hero({ locale, m }: { locale: Locale; m: Messages }) {
  return (
    <section id="top" className={styles.hero}>
      <div className={styles.heroText}>
        <PokedBadge locale={locale} />
        <h1 className={styles.heroTitle}>
          {m.hero.title}
          <br />
          <span className={styles.accentText}>{m.hero.titleAccent}</span>
        </h1>
        <p className={styles.heroLead}>{m.hero.lead}</p>
        <div className={styles.heroActions}>
          <a href="#download" className={styles.primaryButton}>
            {m.hero.primary}
          </a>
          <a href="#how" className={styles.outlineButton}>
            {m.hero.secondary}
          </a>
        </div>
      </div>
      <div className={styles.heroPhone} aria-hidden="true">
        <div className={styles.phone}>
          <span className={styles.phoneLabel}>{m.phone.label}</span>
          <div className={styles.phoneRing}>
            <div className={styles.phoneBell}>
              <svg
                width="30"
                height="30"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                <path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
              </svg>
            </div>
          </div>
          <div className={styles.phoneTask}>{m.phone.task}</div>
          <div className={styles.phoneSub}>{m.phone.sub}</div>
          <div className={styles.spacer} />
          <div className={styles.phoneButton}>{m.phone.button}</div>
        </div>
        <div className={cx(styles.bubbleExcuse, styles.wiggle)}>{m.phone.excuse}</div>
        <div className={styles.bubbleReply}>{m.phone.reply}</div>
      </div>
    </section>
  );
}

function Marquee({ items }: { items: string[] }) {
  // The list runs twice so the scroll loops without a gap; the copy is hidden from screen readers.
  const run = (hidden: boolean) => (
    <span className={styles.marqueeRun} aria-hidden={hidden || undefined}>
      {items.map((item) => (
        <span key={item} className={styles.marqueeItem}>
          {item}
          <span aria-hidden="true">·</span>
        </span>
      ))}
    </span>
  );
  return (
    <div className={styles.marquee}>
      <div className={styles.marqueeTrack}>
        {run(false)}
        {run(true)}
      </div>
    </div>
  );
}

function Steps({ m }: { m: Messages }) {
  return (
    <section id="how" className={styles.light}>
      <div className={styles.container}>
        <h2 className={styles.sectionTitle}>
          {m.steps.title}
          <br />
          {m.steps.titleSecondLine}
        </h2>
        <div className={styles.cards}>
          {m.steps.items.map((step, index) => (
            <div key={step.title} className={styles.stepCard}>
              <div className={styles.stepNumber}>{index + 1}</div>
              <h3 className={styles.stepTitle}>{step.title}</h3>
              <p className={styles.stepBody}>{step.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Levels({ locale, m }: { locale: Locale; m: Messages }) {
  return (
    <section id="levels" className={styles.dark}>
      <div className={cx(styles.container, styles.split)}>
        <div className={styles.levelsIntro}>
          <h2 className={styles.sectionTitle}>
            {m.levels.title}
            <br />
            <span className={styles.accentText}>{m.levels.titleAccent}</span>
          </h2>
          <p className={styles.levelsLead}>{m.levels.lead}</p>
          <PokeButton locale={locale} />
        </div>
        <PokeLevels locale={locale} />
      </div>
    </section>
  );
}

function Excuses({ m }: { m: Messages }) {
  return (
    <section className={styles.orange}>
      <div className={styles.container}>
        <h2 className={styles.sectionTitle}>{m.excuses.title}</h2>
        <div className={styles.wideCards}>
          {m.excuses.items.map((item) => (
            <div key={item.excuse} className={styles.excuseCard}>
              <del className={styles.excuse}>{item.excuse}</del>
              <div className={styles.excuseReply}>{item.reply}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Pricing({ m }: { m: Messages }) {
  const { free, premium } = m.pricing;
  return (
    <section id="pricing" className={styles.light}>
      <div className={styles.container}>
        <h2 className={styles.sectionTitle}>
          {m.pricing.title}
          <br />
          <span className={styles.pokeText}>{m.pricing.titleAccent}</span>
        </h2>
        <div className={styles.wideCards}>
          <div className={styles.planFree}>
            <div className={styles.planLabel}>{free.label}</div>
            <div className={styles.planPrice}>{free.price}</div>
            <ul className={styles.planFeatures}>
              {free.features.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
            <a href="#download" className={styles.planCta}>
              {free.cta}
            </a>
          </div>
          <div className={styles.planPremium}>
            <div className={styles.planHead}>
              <span className={styles.planLabelPremium}>{premium.label}</span>
              <span className={styles.planTag}>{premium.tag}</span>
            </div>
            <div className={styles.planPrice}>{premium.price}</div>
            <ul className={cx(styles.planFeatures, styles.planFeaturesDark)}>
              {premium.features.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}

function Faq({ m }: { m: Messages }) {
  return (
    <section className={styles.dark}>
      <div className={styles.narrow}>
        <h2 className={styles.faqTitle}>{m.faq.title}</h2>
        {m.faq.items.map((item) => (
          <details key={item.question} className={styles.faqItem}>
            <summary className={styles.faqQuestion}>{item.question}</summary>
            <p className={styles.faqAnswer}>{item.answer}</p>
          </details>
        ))}
      </div>
    </section>
  );
}

function Download({ m }: { m: Messages }) {
  return (
    <section id="download" className={styles.orange}>
      <div className={cx(styles.container, styles.download)}>
        <h2 className={styles.downloadTitle}>
          {m.download.title}
          <br />
          {m.download.titleSecondLine}
        </h2>
        <StoreButton url={PLAY_STORE_URL} download={m.download} />
        <div className={styles.downloadNote}>{m.download.iphone}</div>
      </div>
    </section>
  );
}

export function Landing({ locale }: { locale: Locale }) {
  const m = messages[locale];
  return (
    <PokeDemoProvider>
      <div className={styles.page}>
        <Header locale={locale} m={m} />
        <main>
          <Hero locale={locale} m={m} />
          <Marquee items={m.marquee} />
          <section id="story" className={styles.white}>
            <div className={styles.container}>
              <h2 className={styles.sectionTitle}>{m.story.title}</h2>
              <p className={styles.subtitle}>{m.story.subtitle}</p>
              <Story story={m.story} />
            </div>
          </section>
          <Steps m={m} />
          <Levels locale={locale} m={m} />
          <Excuses m={m} />
          <Pricing m={m} />
          <Faq m={m} />
          <Download m={m} />
        </main>
        <footer className={styles.footer}>
          <div className={styles.footerRow}>
            <span>
              <span className={styles.footerBrand}>
                {m.brand}
                <span className={styles.logoDot}>.</span>
              </span>{" "}
              · {m.domain}
            </span>
            {/* TODO: terms and contact links once those pages exist (docs/human-checklist.md, beta). */}
            <span className={styles.footerLinks}>
              <a href={legalPath.privacy[locale]} className={styles.footerLink}>
                {m.footer.privacy}
              </a>
              <a href={legalPath.deleteAccount[locale]} className={styles.footerLink}>
                {m.footer.deleteAccount}
              </a>
            </span>
            <span>{m.footer.quietHours}</span>
          </div>
        </footer>
      </div>
    </PokeDemoProvider>
  );
}
