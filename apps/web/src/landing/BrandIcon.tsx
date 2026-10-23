// The Play store icon for the browser tab and home screens: the logo dot and three poke rings on
// ink, the group turned -12 degrees (same paths as the Android launcher icon, ic_launcher_*.xml).
import { ImageResponse } from "next/og";
import { palette } from "./theme";

export function brandIcon(size: number): ImageResponse {
  return new ImageResponse(
    <div style={{ display: "flex", width: "100%", height: "100%", background: palette.ink }}>
      <svg width={size} height={size} viewBox="0 0 512 512" fill="none">
        <g transform="rotate(-12 256 256)" strokeLinecap="round">
          <path d="M296,186 A100,100 0,0 1,296,326" stroke={palette.accent} strokeWidth="26" />
          <path
            d="M340,150 A150,150 0,0 1,340,362"
            stroke={palette.pokeRing}
            strokeOpacity="0.7"
            strokeWidth="24"
          />
          <path
            d="M384,114 A200,200 0,0 1,384,398"
            stroke={palette.pokeRing}
            strokeOpacity="0.38"
            strokeWidth="22"
          />
          <circle cx="196" cy="256" r="74" fill={palette.accent} />
        </g>
      </svg>
    </div>,
    { width: size, height: size },
  );
}
