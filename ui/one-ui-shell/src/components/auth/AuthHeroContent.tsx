"use client";

const VALUE_CHIPS = ["Human first.", "Trust locked.", "Live now."] as const;

type AuthHeroContentProps = {
  /** Compact layout for mobile/tablet strip above the auth form */
  variant?: "desktop" | "compact";
};

export function AuthHeroContent({ variant = "desktop" }: AuthHeroContentProps) {
  const isCompact = variant === "compact";

  return (
    <div
      className={isCompact ? "space-y-3 text-left" : "space-y-5"}
      data-testid={isCompact ? "auth-mobile-hero-copy" : "auth-desktop-hero-copy"}
    >
      <p
        className={
          isCompact
            ? "text-[11px] font-semibold uppercase tracking-[0.18em] text-amber-200/90"
            : "text-xs font-semibold uppercase tracking-[0.22em] text-amber-200/90"
        }
      >
        One Health OS.
      </p>

      <h2
        className={
          isCompact
            ? "font-serif text-xl font-semibold leading-snug text-white sm:text-2xl"
            : "max-w-lg font-serif text-3xl font-semibold leading-tight text-white xl:text-4xl"
        }
      >
        Your health-life experience, connected.
      </h2>

      <p
        className={
          isCompact
            ? "text-sm leading-relaxed text-white/80"
            : "max-w-md text-lg leading-relaxed text-white/85"
        }
      >
        Care in motion. Work in rhythm. Life in sync.
      </p>

      <div
        className={
          isCompact
            ? "flex flex-wrap gap-2 pt-1"
            : "flex flex-wrap gap-2.5 pt-2"
        }
        role="list"
        aria-label="Platform values"
      >
        {VALUE_CHIPS.map((label) => (
          <span
            key={label}
            role="listitem"
            className={
              isCompact
                ? "inline-flex items-center rounded-full border border-white/15 bg-white/10 px-3 py-1 text-[11px] font-medium text-white/95 backdrop-blur-sm"
                : "inline-flex items-center rounded-full border border-white/20 bg-white/10 px-3.5 py-1.5 text-xs font-medium text-white/95 backdrop-blur-sm"
            }
          >
            {label}
          </span>
        ))}
      </div>
    </div>
  );
}
