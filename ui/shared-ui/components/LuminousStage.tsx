import React from "react";

/**
 * LuminousStage — the "Future-Realism" LIGHT backdrop for wellness / citizen / clinical-care.
 *
 * Doctrine (docs/design/IMPILO_VNEXT_FUTURE_REALISM_STYLE_GUIDE.md §5/§8, "many experience modes"):
 * - The dark CinematicStage suits operational command surfaces (analytics, monitoring). Health &
 *   wellness surfaces want the opposite emotional register: light, warm, airy, human. But a FLAT
 *   light page makes glass invisible — the earlier fall-flat was flatness, not lightness.
 * - This is the light equivalent stage: a luminous near-white gradient with soft teal/gold tint
 *   washes, so GlassSurface children refract and read with depth while staying firmly light. It does
 *   NOT carry `dark` — the light token palette stays in force.
 * - The tint washes are enhancement only: suppressed at the baseline / `.low-blur` tier (emitted by
 *   TierProvider), leaving a clean solid-light surface.
 */
export interface LuminousStageProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode;
  className?: string;
}

export function LuminousStage({ children, className = "", ...rest }: LuminousStageProps) {
  return (
    <div
      data-luminous=""
      className={`relative isolate overflow-hidden rounded-3xl border border-[color:var(--border-soft)] text-[color:var(--text-primary)] shadow-impilo-floating ${className}`}
      style={{
        backgroundImage:
          "radial-gradient(120% 90% at 88% -12%, rgba(35, 230, 160, 0.16), transparent 55%)," +
          "radial-gradient(95% 85% at -8% 112%, rgba(255, 233, 77, 0.14), transparent 52%)," +
          "linear-gradient(160deg, #ffffff 0%, #f3f9f5 46%, #edf6f7 100%)",
      }}
      {...rest}
    >
      {/* Soft tint washes — enhancement only; suppressed at baseline / low-blur tier. */}
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 [.low-blur_&]:hidden">
        <div
          className="absolute -top-24 -right-20 h-72 w-72 rounded-full blur-3xl"
          style={{ backgroundColor: "rgba(35, 230, 160, 0.18)" }}
        />
        <div
          className="absolute -bottom-28 -left-20 h-80 w-80 rounded-full blur-3xl"
          style={{ backgroundColor: "rgba(255, 233, 77, 0.16)" }}
        />
      </div>
      {children}
    </div>
  );
}
