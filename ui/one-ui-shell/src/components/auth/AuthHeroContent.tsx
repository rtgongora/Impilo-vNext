"use client";

import { Heart, Lock, Sparkles } from "lucide-react";

const VALUE_CHIPS = [
  { label: "Human first.", Icon: Heart },
  { label: "Trust locked.", Icon: Lock },
  { label: "Live now.", Icon: Sparkles },
] as const;

type AuthHeroContentProps = {
  /** Compact layout for mobile/tablet strip above the auth form */
  variant?: "desktop" | "compact";
};

export function AuthHeroContent({ variant = "desktop" }: AuthHeroContentProps) {
  const isCompact = variant === "compact";

  return (
    <div
      className={isCompact ? "relative z-10 space-y-3 text-left" : "relative z-10 space-y-5"}
      data-testid={isCompact ? "auth-mobile-hero-copy" : "auth-desktop-hero-copy"}
    >
      <p
        className={
          isCompact
            ? "flex items-baseline gap-1.5 font-rounded text-lg font-semibold tracking-tight text-white sm:text-xl"
            : "flex items-baseline gap-2 font-rounded text-2xl font-semibold tracking-tight text-white xl:text-[1.75rem]"
        }
      >
        <span
          aria-hidden
          className={
            isCompact
              ? "inline-block h-2 w-2 shrink-0 translate-y-[-0.1em] rounded-full bg-[#D4AF37]"
              : "inline-block h-2.5 w-2.5 shrink-0 translate-y-[-0.15em] rounded-full bg-[#D4AF37]"
          }
        />
        One Health OS.
      </p>

      <h2
        className={
          isCompact
            ? "max-w-sm font-sans text-xl font-medium leading-snug tracking-tight text-white sm:text-2xl"
            : "max-w-lg font-sans text-3xl font-medium leading-[1.15] tracking-tight text-white xl:text-[2.25rem]"
        }
      >
        Your health-life experience, connected.
      </h2>

      <div
        className={
          isCompact
            ? "space-y-0.5 text-sm leading-relaxed text-white/80"
            : "max-w-md space-y-1 text-base leading-relaxed text-white/85 xl:text-lg"
        }
      >
        <p>Care in motion.</p>
        <p>Work in rhythm.</p>
        <p>Life in sync.</p>
      </div>

      <div
        className={isCompact ? "flex flex-wrap gap-2 pt-1" : "flex flex-wrap gap-2.5 pt-2"}
        role="list"
        aria-label="Platform values"
      >
        {VALUE_CHIPS.map(({ label, Icon }) => (
          <span
            key={label}
            role="listitem"
            className={
              isCompact
                ? "inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/[0.08] px-3 py-1 text-[11px] font-medium text-white/95 backdrop-blur-sm"
                : "inline-flex items-center gap-1.5 rounded-full border border-white/20 bg-white/[0.08] px-3.5 py-1.5 text-xs font-medium text-white/95 backdrop-blur-sm"
            }
          >
            <Icon className="h-3 w-3 shrink-0 text-[#D4AF37]" aria-hidden />
            {label}
          </span>
        ))}
      </div>
    </div>
  );
}
