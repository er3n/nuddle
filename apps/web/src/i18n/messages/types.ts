// The shape every web dictionary fills. Turkish (`tr.ts`) is the source language.

interface Titled {
  title: string;
  body: string;
}

interface StoryPanel {
  time: string;
  caption: string;
  /** Describes the drawing for screen readers. */
  alt: string;
}

export interface Messages {
  brand: string;
  domain: string;
  meta: { title: string; description: string };
  nav: {
    label: string;
    how: string;
    levels: string;
    pricing: string;
    download: string;
    /** Links to the other language, written in that language. */
    otherLanguage: string;
  };
  hero: {
    pokedYou: (count: number) => string;
    title: string;
    titleAccent: string;
    lead: string;
    primary: string;
    secondary: string;
  };
  phone: {
    label: string;
    task: string;
    sub: string;
    button: string;
    excuse: string;
    reply: string;
  };
  marquee: string[];
  story: {
    title: string;
    subtitle: string;
    panels: [StoryPanel, StoryPanel, StoryPanel, StoryPanel];
    /** Words drawn inside the pictures. */
    drawn: {
      order: string;
      firstReply: string;
      loudOrder: string;
      secondReply: string;
      fullScreen: string;
      approved: string;
    };
  };
  steps: { title: string; titleSecondLine: string; items: [Titled, Titled, Titled] };
  levels: {
    title: string;
    titleAccent: string;
    lead: string;
    /** One label per level reached, from none to all five. */
    button: [string, string, string, string, string, string];
    items: [Titled, Titled, Titled, Titled, Titled];
  };
  excuses: { title: string; items: { excuse: string; reply: string }[] };
  pricing: {
    title: string;
    titleAccent: string;
    free: { label: string; price: string; features: string[]; cta: string };
    premium: { label: string; tag: string; price: string; features: string[] };
  };
  faq: { title: string; items: { question: string; answer: string }[] };
  download: {
    title: string;
    titleSecondLine: string;
    store: string;
    storeSoon: string;
    iphone: string;
  };
  footer: { quietHours: string; privacy: string; deleteAccount: string };
  invite: { title: string; body: string; more: string };
  notFound: { title: string; home: string };
  legal: Legal;
}

/** One part of a legal page: a heading, its paragraphs and an optional list after them. */
interface LegalSection {
  heading: string;
  paragraphs: string[];
  items?: string[];
}

interface LegalPage {
  /** The browser tab title. */
  metaTitle: string;
  title: string;
  updated: string;
  intro: string;
  sections: LegalSection[];
}

/** The privacy notice and the account deletion page (docs/human-checklist.md, Before the beta). */
export interface Legal {
  privacy: LegalPage;
  deleteAccount: LegalPage;
  /** Links back to the landing page. */
  home: string;
  /** Links to the same page in the other language, written in that language. */
  otherLanguage: string;
}
