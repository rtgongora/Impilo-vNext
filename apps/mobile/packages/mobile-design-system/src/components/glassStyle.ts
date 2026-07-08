import type { ViewStyle } from "react-native";
import { glass, neon, borderRadius, shadows } from "../tokens";

/**
 * Pure (RN-runtime-free) style resolver for GlassSurface — unit-testable in isolation.
 * `import type { ViewStyle }` is erased at build, so this module imports no native code.
 * See docs/design/IMPILO_VNEXT_FUTURE_REALISM_STYLE_GUIDE.md.
 */
export type GlassTone = "dark" | "light";
export type GlassGlow = "none" | "teal" | "gold" | "danger";
export type GlassTier = "baseline" | "enhanced" | "cinematic";

const GLOW_COLOR: Record<GlassGlow, string | undefined> = {
  none: undefined,
  teal: neon.teal,
  gold: neon.gold,
  danger: "#EF3340",
};

/** baseline → solid fallback fill (AA-legible); enhanced/cinematic → translucent fill + optional glow. */
export function resolveGlassStyle(
  tone: GlassTone = "dark",
  glow: GlassGlow = "none",
  tier: GlassTier = "enhanced",
): ViewStyle {
  const solid = tier === "baseline";
  const fill = solid
    ? tone === "dark"
      ? glass.fallbackDark
      : glass.fallbackLight
    : tone === "dark"
      ? glass.fillDark
      : glass.fillLight;

  const base: ViewStyle = {
    backgroundColor: fill,
    borderWidth: 1,
    borderColor: glass.border,
    borderRadius: borderRadius.xl,
    ...shadows.lg,
  };

  const glowColor = GLOW_COLOR[glow];
  if (glowColor && !solid) {
    return { ...base, shadowColor: glowColor, shadowOpacity: 0.45, shadowRadius: 16, elevation: 12 };
  }
  return base;
}
