"use client";

import Image from "next/image";

/**
 * Large subtle Impilo mark ghost watermark for auth hero panels.
 * Uses the canonical mark asset from /brand/mark-white.svg.
 */
export function AuthHeroWatermark({ compact = false }: { compact?: boolean }) {
  return (
    <div
      className={
        compact
          ? "pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden"
          : "pointer-events-none absolute inset-0 overflow-hidden"
      }
      aria-hidden
      data-testid="auth-hero-watermark"
    >
      <Image
        src="/brand/mark-white.svg"
        alt=""
        width={compact ? 280 : 480}
        height={compact ? 280 : 480}
        className={
          compact
            ? "select-none opacity-[0.08] blur-[0.5px]"
            : "absolute -right-8 top-1/2 -translate-y-1/2 select-none opacity-[0.07] blur-[0.5px] sm:-right-4 lg:right-8 xl:right-12"
        }
        draggable={false}
        unoptimized
      />
    </div>
  );
}
