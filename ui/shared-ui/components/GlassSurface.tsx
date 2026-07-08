import React from "react";

/**
 * GlassSurface — the "Future-Realism" frosted-glass container primitive.
 *
 * Doctrine (see docs/design/IMPILO_VNEXT_FUTURE_REALISM_STYLE_GUIDE.md):
 * - Backdrop blur is **progressive enhancement**. The surface always paints a solid, opaque
 *   fallback (`--glass-fallback`) so text on it stays AA-legible regardless of what is behind.
 * - The fallback engages automatically when: the browser lacks `backdrop-filter` support, OR an
 *   ancestor carries `.low-blur` (emitted by the TierProvider for the baseline tier /
 *   `prefers-reduced-transparency` / save-data / low-memory devices).
 * - `glow` is a restrained neon accent; use at most one focal glow per region.
 */
export interface GlassSurfaceProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode;
  /** Blur strength (enhancement only; ignored at baseline tier). */
  blur?: "soft" | "strong";
  /** Optional single focal glow accent. `danger` is reserved for emergency/SOS. */
  glow?: "none" | "teal" | "gold" | "danger";
  className?: string;
}

const glowClass: Record<string, string> = {
  none: "",
  teal: "shadow-glow-teal",
  gold: "shadow-glow-gold",
  danger: "shadow-glow-danger",
};

export function GlassSurface({
  children,
  blur = "soft",
  glow = "none",
  className = "",
  ...rest
}: GlassSurfaceProps) {
  const blurClass = blur === "strong" ? "backdrop-blur-glass-strong" : "backdrop-blur-glass";

  return (
    <div
      data-glass=""
      className={`rounded-3xl border border-glass-border bg-glass-fill shadow-impilo-floating ${blurClass}
        supports-[not(backdrop-filter:blur(0px))]:bg-glass-fallback
        [.low-blur_&]:bg-glass-fallback [.low-blur_&]:backdrop-blur-none
        ${glowClass[glow]} ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}
