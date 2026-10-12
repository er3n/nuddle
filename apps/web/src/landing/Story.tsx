// "Bir ekmeğin hikayesi": four drawings from Landing.dc.html. Words drawn inside them are localized.
import type { ReactNode } from "react";
import type { Messages } from "../i18n/messages/types";
import styles from "./landing.module.css";
import { palette as p } from "./theme";

type Drawn = Messages["story"]["drawn"];

const line = { stroke: p.ink, strokeWidth: 3, strokeLinecap: "round" } as const;
const outlined = { stroke: p.ink, strokeWidth: 3 } as const;

function Panel({ alt, children }: { alt: string; children: ReactNode }) {
  return (
    <svg viewBox="0 0 300 240" width="100%" role="img" aria-label={alt} className={styles.drawing}>
      <path d="M0 216h300" {...outlined} />
      {children}
    </svg>
  );
}

function Television() {
  return (
    <>
      <rect x="10" y="64" width="60" height="42" rx="6" fill={p.ink} />
      <rect x="16" y="70" width="48" height="30" rx="3" fill={p.screen} />
      <path d="M40 106v12M28 118h24" {...line} />
    </>
  );
}

/** The couch, the person on it and the game controller; `face` draws the expression. */
function Couch({ face }: { face: ReactNode }) {
  return (
    <>
      <rect x="84" y="150" width="194" height="56" rx="18" fill={p.accent} {...outlined} />
      <rect x="74" y="136" width="26" height="70" rx="12" fill={p.accent} {...outlined} />
      <rect x="262" y="136" width="26" height="70" rx="12" fill={p.accent} {...outlined} />
      <rect x="140" y="102" width="64" height="70" rx="28" fill={p.surface} {...outlined} />
      <circle cx="172" cy="78" r="28" fill={p.skin} {...outlined} />
      {face}
      <rect x="148" y="124" width="46" height="18" rx="9" fill={p.ink} />
      <circle cx="184" cy="133" r="3" fill={p.accent} />
      <circle cx="158" cy="133" r="3" fill={p.surface} />
    </>
  );
}

function Thought({ x, width, text }: { x: number; width: number; text: string }) {
  return (
    <>
      <rect x={x} y="12" width={width} height="32" rx="16" fill={p.surface} {...outlined} />
      <text
        x={x + width / 2}
        y="33"
        textAnchor="middle"
        fontSize="14"
        fontWeight="600"
        fill={p.ink}
      >
        {text}
      </text>
    </>
  );
}

function Calm({ drawn }: { drawn: Drawn }) {
  return (
    <>
      <Television />
      <Couch
        face={
          <>
            <circle cx="161" cy="76" r="3.5" fill={p.ink} />
            <circle cx="177" cy="76" r="3.5" fill={p.ink} />
            <path d="M162 91q7 4 14 -1" fill="none" {...line} />
          </>
        }
      />
      <rect x="268" y="100" width="20" height="34" rx="4" fill={p.ink} />
      <path d="M262 104l-6-3M262 116h-7" {...line} strokeWidth={2.5} />
      <rect x="198" y="16" width="94" height="34" rx="12" fill={p.surface} {...outlined} />
      <text x="245" y="38" textAnchor="middle" fontSize="15" fontWeight="700" fill={p.ink}>
        {drawn.order}
      </text>
      <path d="M268 50l8 14 2-14" fill={p.surface} {...outlined} strokeLinejoin="round" />
      <Thought x={64} width={118} text={drawn.firstReply} />
      <circle cx="148" cy="50" r="3.5" fill={p.surface} stroke={p.ink} strokeWidth={2} />
    </>
  );
}

function Loud({ drawn }: { drawn: Drawn }) {
  return (
    <>
      <Television />
      <Couch
        face={
          <>
            <path d="M156 77h10M172 77h10" {...line} strokeWidth={3.5} />
            <path d="M164 92h12" {...line} />
          </>
        }
      />
      <rect x="262" y="88" width="28" height="46" rx="5" fill={p.ink} />
      <rect x="266" y="94" width="20" height="32" rx="2" fill={p.accent} />
      <path d="M256 90l-8-5M256 104h-10M256 118l-8 5M296 84l4-6M296 130l4 6" {...line} />
      <rect x="190" y="14" width="102" height="40" rx="12" fill={p.accent} {...outlined} />
      <text x="241" y="41" textAnchor="middle" fontSize="18" fontWeight="700" fill={p.ink}>
        {drawn.loudOrder}
      </text>
      <path d="M266 54l8 24 4-24" fill={p.accent} {...outlined} strokeLinejoin="round" />
      <Thought x={74} width={100} text={drawn.secondReply} />
      <circle cx="146" cy="50" r="3.5" fill={p.surface} stroke={p.ink} strokeWidth={2} />
    </>
  );
}

function FullScreen({ drawn }: { drawn: Drawn }) {
  return (
    <>
      <rect x="44" y="150" width="140" height="56" rx="18" fill={p.accent} {...outlined} />
      <rect x="34" y="136" width="26" height="70" rx="12" fill={p.accent} {...outlined} />
      <rect x="86" y="102" width="64" height="70" rx="28" fill={p.surface} {...outlined} />
      <path d="M104 52l-4-14M118 48l0-16M132 52l5-14" {...line} />
      <circle cx="118" cy="78" r="28" fill={p.skin} {...outlined} />
      <circle cx="108" cy="74" r="9" fill={p.surface} stroke={p.ink} strokeWidth={2.5} />
      <circle cx="130" cy="74" r="9" fill={p.surface} stroke={p.ink} strokeWidth={2.5} />
      <circle cx="112" cy="74" r="3.5" fill={p.ink} />
      <circle cx="134" cy="74" r="3.5" fill={p.ink} />
      <ellipse cx="120" cy="94" rx="6" ry="7" fill={p.ink} />
      <g transform="rotate(-28 40 40)">
        <rect x="18" y="30" width="46" height="18" rx="9" fill={p.ink} />
      </g>
      <path d="M70 52l12 6M66 64l12 2" {...line} strokeWidth={2.5} />
      <rect x="176" y="14" width="114" height="190" rx="16" fill={p.ink} />
      <rect x="183" y="24" width="100" height="170" rx="10" fill={p.poke} />
      <text x="233" y="112" textAnchor="middle" fontSize="26" fontWeight="700" fill={p.surface}>
        {drawn.fullScreen}
      </text>
      <rect x="196" y="160" width="74" height="22" rx="8" fill={p.surface} />
      <path d="M168 30l-10-6M166 70h-12M168 120l-10 4M168 170l-10 6" {...line} />
    </>
  );
}

function Proof({ drawn }: { drawn: Drawn }) {
  return (
    <>
      <rect x="14" y="30" width="78" height="186" fill={p.door} {...outlined} />
      <circle cx="80" cy="128" r="4" fill={p.ink} />
      <path d="M140 188l-6 26M180 188l6 26" {...line} strokeWidth={4} />
      <rect x="128" y="104" width="64" height="88" rx="28" fill={p.surface} {...outlined} />
      <circle cx="160" cy="80" r="28" fill={p.skin} {...outlined} />
      <path d="M146 78q5-6 10 0M164 78q5-6 10 0" fill="none" {...line} />
      <path d="M148 90q12 12 24 0" fill={p.surface} {...line} />
      <path d="M194 64q6 10 0 14q-6-4 0-14z" fill={p.sweat} stroke={p.ink} strokeWidth={2} />
      <ellipse cx="214" cy="150" rx="54" ry="24" fill={p.bread} {...outlined} />
      <path d="M188 140l10 16M208 136l10 18M228 136l10 18" {...line} stroke={p.breadCrust} />
      <path d="M190 130q12 8 6 22" fill="none" {...line} />
      <path d="M130 130q-16 -6 -20 -22" fill="none" {...line} />
      <rect x="100" y="90" width="16" height="26" rx="3" fill={p.ink} />
      <path
        d="M118 70l4 10 10 -2 -6 8 6 8 -10 -2 -4 10 -3 -10 -10 2 6 -8 -6 -8 10 2z"
        fill={p.sparkle}
        stroke={p.ink}
        strokeWidth={2}
        strokeLinejoin="round"
      />
      <rect x="196" y="14" width="96" height="34" rx="12" fill={p.success} {...outlined} />
      <path d="M208 31l5 5 9-10" fill="none" {...line} stroke={p.surface} strokeLinejoin="round" />
      <text x="256" y="36" textAnchor="middle" fontSize="14" fontWeight="700" fill={p.surface}>
        {drawn.approved}
      </text>
    </>
  );
}

const scenes = [Calm, Loud, FullScreen, Proof] as const;

export function Story({ story }: { story: Messages["story"] }) {
  return (
    <div className={styles.storyGrid}>
      {story.panels.map((panel, index) => {
        const Scene = scenes[index] ?? Calm;
        return (
          <figure key={panel.time} className={styles.storyPanel}>
            <Panel alt={panel.alt}>
              <Scene drawn={story.drawn} />
            </Panel>
            <figcaption className={styles.storyCaption}>
              <strong>{panel.time}</strong> · {panel.caption}
            </figcaption>
          </figure>
        );
      })}
    </div>
  );
}
