// Web colors come from the same tokens as the app theme (docs/ux/tokens.json); no hex values here.
import type { CSSProperties } from "react";
import tokens from "../../../../docs/ux/tokens.json";

const { color } = tokens;

export const palette = {
  ink: color.ink.value,
  surface: color.surface.value,
  background: color.background.value,
  textBody: color.textBody.value,
  textSecondary: color.textSecondary.value,
  textTertiary: color.textTertiary.value,
  textMuted: color.textMuted.value,
  accent: color.accent.value,
  link: color.link.value,
  poke: color.poke.value,
  pokeDeep: color.pokeDeep.value,
  pokeRing: color.pokeRing.value,
  onPokeSoft: color.onPokeSoft.value,
  success: color.success.value,
  highlightSoft: color.highlightSoft.value,
  skin: color.warningPill.value,
  door: color.neutralPill.value,
  darkSurface: color.dark.surface,
  darkBorder: color.dark.border,
  darkTextSecondary: color.dark.textSecondary,
  screen: color.illustration.screen,
  bread: color.illustration.bread,
  breadCrust: color.illustration.breadCrust,
  sweat: color.illustration.sweat,
  sparkle: color.illustration.sparkle,
} as const;

const kebab = (name: string) => name.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);

/** The palette as CSS custom properties (`--text-body`), set once on `<body>`. */
export const paletteVars = Object.fromEntries(
  Object.entries(palette).map(([name, value]) => [`--${kebab(name)}`, value]),
) as CSSProperties;
