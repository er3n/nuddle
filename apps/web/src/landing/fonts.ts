// Fonts from docs/ux/tokens.json (typography.families), self-hosted by next/font at build time.
import { IBM_Plex_Sans, Space_Grotesk } from "next/font/google";

const display = Space_Grotesk({
  subsets: ["latin", "latin-ext"],
  weight: ["500", "600", "700"],
  variable: "--font-display",
});

const text = IBM_Plex_Sans({
  subsets: ["latin", "latin-ext"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-text",
});

export const fontVariables = `${display.variable} ${text.variable}`;
