/**
 * Design Tokens — Re-exports all token categories.
 */

import { colors } from "./colors";
import { spacing } from "./spacing";
import { typography, textStyles } from "./typography";
import { gloss } from "./gloss";

export { colors } from "./colors";
export type { ColorToken } from "./colors";

export { spacing } from "./spacing";
export type { SpacingToken } from "./spacing";

export { typography, textStyles } from "./typography";
export type { TextStyle } from "./typography";

export {
  gloss,
  glossRamp,
  glossFloor,
  glossInkOnLight,
  glossInkOnDark,
  glossPill,
  glossSheen,
  glossButtonStops,
  rampColorAt,
  rampBands,
  lighten,
  mix,
} from "./gloss";

export const borderRadius = {
  none: 0,
  sm: 4,
  md: 8,
  lg: 12,
  xl: 16,
  full: 9999,
} as const;

export const shadows = {
  none: { shadowColor: "transparent", shadowOffset: { width: 0, height: 0 }, shadowOpacity: 0, shadowRadius: 0, elevation: 0 },
  sm: { shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 2, elevation: 1 },
  md: { shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 4, elevation: 3 },
  lg: { shadowColor: "#000", shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.15, shadowRadius: 8, elevation: 6 },
  xl: { shadowColor: "#000", shadowOffset: { width: 0, height: 8 }, shadowOpacity: 0.2, shadowRadius: 16, elevation: 12 },
} as const;

/**
 * Future-Realism accents — GLOW/ACCENT ONLY (never body text or clinical semantics).
 * `glow.*` are rgba strings driving RN shadow / Skia glow strength.
 */
export const neon = {
  teal: "#23E6A0",
  gold: "#FFE94D",
  glow: {
    teal: "rgba(35, 230, 160, 0.45)",
    gold: "rgba(255, 233, 77, 0.4)",
    danger: "rgba(239, 51, 64, 0.45)", // reserved for emergency/SOS
  },
} as const;

/**
 * Glassmorphism tokens. `blur*` feed <BlurView intensity>; text on glass must pass AA
 * against the SOLID fallback (used when blur is unsupported / low device tier).
 */
export const glass = {
  blurSoft: 12,
  blurStrong: 25,
  border: "rgba(255, 255, 255, 0.16)",
  fillDark: "rgba(20, 28, 24, 0.55)",
  fillLight: "rgba(255, 255, 255, 0.62)",
  fallbackDark: "#141C18",
  fallbackLight: "#F4F7F5",
} as const;

export const tokens = {
  colors,
  spacing,
  typography,
  textStyles,
  borderRadius,
  shadows,
  neon,
  glass,
  gloss,
} as const;
