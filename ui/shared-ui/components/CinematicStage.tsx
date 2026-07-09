import React from "react";

/**
 * CinematicStage — the "Future-Realism" dark command-center backdrop.
 *
 * Doctrine (docs/design/IMPILO_VNEXT_FUTURE_REALISM_STYLE_GUIDE.md §5/§8):
 * - Glassmorphism needs a rich backdrop to refract; a GlassSurface on a flat light page is
 *   invisible. This primitive is that stage: a deep teal-charcoal gradient with restrained
 *   teal/gold aurora, so GlassSurface children finally read with depth + glow.
 * - It carries the `dark` class, so its whole subtree resolves the AA-tuned dark token palette
 *   (Tailwind darkMode:"class" scopes to any ancestor — no global <html> flip needed, keeping the
 *   surrounding clinical shell on its light theme).
 * - The blurred aurora is enhancement only: hidden at the baseline / `.low-blur` tier (emitted by
 *   TierProvider for reduced-transparency / save-data / low-memory), leaving a clean solid dark.
 */
export interface CinematicStageProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode;
  className?: string;
}

export function CinematicStage({ children, className = "", ...rest }: CinematicStageProps) {
  return (
    <div
      data-cinematic=""
      className={`dark relative isolate overflow-hidden rounded-3xl border border-[color:var(--border-strong)] text-[color:var(--text-primary)] shadow-impilo-floating ${className}`}
      style={{
        backgroundImage:
          "radial-gradient(120% 90% at 85% -10%, rgba(35, 230, 160, 0.16), transparent 55%)," +
          "radial-gradient(90% 80% at -10% 115%, rgba(255, 233, 77, 0.09), transparent 50%)," +
          "linear-gradient(160deg, #06100c 0%, #0c110f 46%, #0e1a15 100%)",
      }}
      {...rest}
    >
      {/* Aurora — enhancement only; suppressed at baseline / low-blur tier. */}
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 [.low-blur_&]:hidden">
        <div
          className="absolute -top-24 -right-24 h-72 w-72 rounded-full blur-3xl"
          style={{ backgroundColor: "rgba(35, 230, 160, 0.22)" }}
        />
        <div
          className="absolute -bottom-28 -left-20 h-80 w-80 rounded-full blur-3xl"
          style={{ backgroundColor: "rgba(255, 233, 77, 0.10)" }}
        />
      </div>
      {children}
    </div>
  );
}
