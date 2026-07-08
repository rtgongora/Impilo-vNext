import React from "react";
import { View, type ViewProps } from "react-native";
import { resolveGlassStyle, type GlassTone, type GlassGlow, type GlassTier } from "./glassStyle";

/**
 * GlassSurface (mobile) — the Future-Realism frosted-glass container.
 *
 * Honesty/perf note (see docs/design/IMPILO_VNEXT_FUTURE_REALISM_STYLE_GUIDE.md):
 * - True backdrop blur needs `expo-blur` (BlurView), a **later, tier-gated enhancement**. Until then
 *   "glass" renders as a token-driven translucent surface — never fabricated depth.
 * - At the `baseline` tier it paints a SOLID fallback fill so content stays AA-legible.
 * - `glow` is a restrained neon accent; `danger` is reserved for emergency/SOS.
 *
 * Style resolution lives in ./glassStyle (RN-runtime-free, unit-tested).
 */
export type { GlassTone, GlassGlow, GlassTier } from "./glassStyle";
export { resolveGlassStyle } from "./glassStyle";

export interface GlassSurfaceProps extends ViewProps {
  tone?: GlassTone;
  glow?: GlassGlow;
  /** Resolved device/preference tier — pass from `useTier()`. `baseline` forces the solid fallback. */
  tier?: GlassTier;
  children?: React.ReactNode;
}

export function GlassSurface({
  tone = "dark",
  glow = "none",
  tier = "enhanced",
  style,
  children,
  ...rest
}: GlassSurfaceProps) {
  return (
    <View style={[resolveGlassStyle(tone, glow, tier), style]} {...rest}>
      {children}
    </View>
  );
}
