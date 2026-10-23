"use client";
// The Play promo video in the page's language (NUD-94). The poster is ours; YouTube loads only
// after the play tap, so opening the page sends nothing to a third party.
import { useState } from "react";
import type { Locale } from "../i18n/locales";
import type { Messages } from "../i18n/messages/types";
import styles from "./landing.module.css";
import { POSTER_SIZE, promoVideo } from "./promo";

export { promoVideo } from "./promo";

export function embedUrl(youtubeId: string): string {
  return `https://www.youtube-nocookie.com/embed/${youtubeId}?autoplay=1&rel=0&playsinline=1`;
}

export function PromoVideo({ locale, video }: { locale: Locale; video: Messages["video"] }) {
  const [playing, setPlaying] = useState(false);
  const { youtubeId, poster } = promoVideo[locale];
  return (
    <div className={styles.videoFrame}>
      {playing ? (
        <iframe
          className={styles.videoPlayer}
          src={embedUrl(youtubeId)}
          title={video.title}
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
        />
      ) : (
        <button
          type="button"
          className={styles.videoPoster}
          aria-label={video.play}
          onClick={() => {
            setPlaying(true);
          }}
        >
          <img
            src={poster}
            alt={video.posterAlt}
            className={styles.videoImage}
            width={POSTER_SIZE.width}
            height={POSTER_SIZE.height}
            loading="lazy"
          />
          <span className={styles.videoPlay} aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
              <path d="M8 5.5v13a1 1 0 0 0 1.5.9l10.4-6.5a1 1 0 0 0 0-1.8L9.5 4.6A1 1 0 0 0 8 5.5z" />
            </svg>
            {video.watch}
          </span>
        </button>
      )}
    </div>
  );
}
