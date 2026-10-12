"use client";
// The "Dürtme seviyeleri" toy (Landing.dc.html): each tap lights the next level, and the hero
// badge counts the pokes.
import { createContext, use, useState, type ReactNode } from "react";
import type { Locale } from "../i18n/locales";
import { messages } from "../i18n/messages";
import { cx } from "./cx";
import styles from "./landing.module.css";

const LEVEL_COUNT = 5;

/** One tap: the next level, back to none after the last one. */
export function nextLevel(level: number): number {
  return level >= LEVEL_COUNT ? 0 : level + 1;
}

interface PokeState {
  level: number;
  pokes: number;
  poke: () => void;
}

const PokeContext = createContext<PokeState>({ level: 0, pokes: 0, poke: () => undefined });

export function PokeDemoProvider({ children }: { children: ReactNode }) {
  const [level, setLevel] = useState(0);
  const [pokes, setPokes] = useState(0);
  const poke = () => {
    const next = nextLevel(level);
    setLevel(next);
    if (next > 0) setPokes(pokes + 1);
  };
  return <PokeContext value={{ level, pokes, poke }}>{children}</PokeContext>;
}

export function PokedBadge({ locale }: { locale: Locale }) {
  const { pokes } = use(PokeContext);
  return (
    <div className={cx(styles.badge, styles.wiggle)} aria-live="polite">
      {messages[locale].hero.pokedYou(pokes)}
    </div>
  );
}

function levelClass(index: number, level: number): string {
  const lit = index < level;
  const current = index === level - 1;
  return cx(
    styles.level,
    lit && (index >= 3 ? styles.levelLoud : styles.levelLit),
    current && styles.levelCurrent,
    current && styles.shake,
  );
}

export function PokeButton({ locale }: { locale: Locale }) {
  const { level, poke } = use(PokeContext);
  return (
    <button type="button" className={styles.pokeButton} onClick={poke}>
      {messages[locale].levels.button[level]}
    </button>
  );
}

export function PokeLevels({ locale }: { locale: Locale }) {
  const { level } = use(PokeContext);
  return (
    <ol className={styles.levels}>
      {messages[locale].levels.items.map((item, index) => (
        // The key changes with the level so the current row shakes again on every tap.
        <li
          key={`${item.title}-${String(index === level - 1 ? level : 0)}`}
          className={levelClass(index, level)}
        >
          <span className={styles.levelNumber}>{index + 1}</span>
          <span>
            <span className={styles.levelName}>{item.title}</span>
            <span className={styles.levelSay}>{item.body}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}
