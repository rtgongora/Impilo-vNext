"use client";

/**
 * PrivacyBadge — visible identity-visibility indicator for clinical sessions,
 * case presentations, MDT/audit/teaching surfaces.
 *
 * Presentation-layer only until Tshepo/OPA enforcement lands (HO-4); badges
 * make the intended privacy level explicit so no surface silently over-exposes
 * patient identity.
 */

import { ShieldCheck, ShieldAlert, EyeOff } from "lucide-react";
import { PRIVACY_LEVELS, type IdentityVisibilityLevel } from "@/lib/telemedicine/privacy";

export function PrivacyBadge({
  level,
  showDescription = false,
}: {
  level: IdentityVisibilityLevel;
  showDescription?: boolean;
}) {
  const descriptor = PRIVACY_LEVELS[level];
  const Icon =
    level === "EMERGENCY_ACCESS" || level === "SENSITIVE_RESTRICTED"
      ? ShieldAlert
      : level === "ANONYMISED" || level === "PSEUDONYMISED"
        ? EyeOff
        : ShieldCheck;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${descriptor.badgeClassName}`}
      title={descriptor.description}
    >
      <Icon className="h-3.5 w-3.5" aria-hidden />
      {descriptor.label}
      {showDescription ? (
        <span className="font-normal opacity-80">— {descriptor.description}</span>
      ) : null}
    </span>
  );
}
