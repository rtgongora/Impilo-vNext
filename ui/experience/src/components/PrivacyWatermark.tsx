"use client";

/**
 * PrivacyWatermark — Subtle diagonal watermark overlay for screen-share
 * and screenshot deterrence.
 *
 * Per Health OS doctrine: "reasonable technical safeguards designed to
 * protect information against unauthorized access."
 *
 * Shows the current user's Health ID and timestamp as a repeating
 * diagonal watermark across the viewport. This:
 *   - Deters unauthorized screenshots/screen recordings
 *   - Provides forensic traceability if screens are leaked
 *   - Is only visible in PARTIAL and MINIMAL privacy modes
 *   - Does not interfere with normal clinical workflow
 */

import { useAuthStore } from "@/hooks/useAuthStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";

export function PrivacyWatermark() {
  const user = useAuthStore((s) => s.user);
  const level = usePrivacyDisplayStore((s) => s.level);

  // Only show watermark in PARTIAL or MINIMAL privacy modes
  if (level === "FULL" || !user) return null;

  const watermarkText = `IMPILO ${user.id.slice(0, 8)} ${new Date().toLocaleDateString()}`;

  return (
    <div
      className="fixed inset-0 pointer-events-none z-[9990] overflow-hidden select-none"
      aria-hidden="true"
    >
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: `repeating-linear-gradient(
            -45deg,
            transparent,
            transparent 200px,
            rgba(0,0,0,0.015) 200px,
            rgba(0,0,0,0.015) 201px
          )`,
        }}
      />
      {/* Repeating watermark text grid */}
      <div className="absolute inset-0 flex flex-wrap gap-y-32 gap-x-16 -rotate-[30deg] origin-center scale-125 -translate-x-1/4 -translate-y-1/4">
        {Array.from({ length: 24 }, (_, i) => (
          <span
            key={i}
            className="text-[11px] font-mono tracking-wider whitespace-nowrap"
            style={{
              color: level === "MINIMAL" ? "rgba(0,0,0,0.04)" : "rgba(0,0,0,0.025)",
            }}
          >
            {watermarkText}
          </span>
        ))}
      </div>
    </div>
  );
}
