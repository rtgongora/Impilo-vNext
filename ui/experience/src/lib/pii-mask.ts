/**
 * PII Masking Utilities — Privacy-first display of personal information.
 *
 * Centralised masking functions for all PII fields. Components import
 * these instead of rendering raw PII directly. The masking level is
 * determined by the PrivacyDisplayStore.
 *
 * Per Health OS doctrine:
 *   - "No PII in SHR" — clinical records use CPID, not direct identifiers
 *   - "Minimum-necessary access" — show only what the current context requires
 *   - "Graduated friction" — MINIMAL for low-context, FULL for active treatment
 */

import type { PrivacyLevel } from "@/hooks/usePrivacyDisplayStore";

// ── Name masking ────────────────────────────────────────────────

/**
 * Mask a display name based on privacy level.
 *
 * FULL:    "Tariro Moyo"
 * PARTIAL: "Tariro M."
 * MINIMAL: "T.M."
 */
export function maskName(name: string | undefined | null, level: PrivacyLevel): string {
  if (!name) return "—";
  if (level === "FULL") return name;

  const parts = name.trim().split(/\s+/);
  if (parts.length === 0) return "—";

  if (level === "PARTIAL") {
    if (parts.length === 1) return parts[0];
    return `${parts[0]} ${parts[parts.length - 1][0]}.`;
  }

  // MINIMAL — initials only
  return parts.map((p) => `${p[0]}.`).join("");
}

// ── Date of birth masking ───────────────────────────────────────

/**
 * Mask date of birth based on privacy level.
 *
 * FULL:    "1991-06-11"
 * PARTIAL: "32y" (age only)
 * MINIMAL: "30-39y" (age decade range)
 */
export function maskDob(
  dob: string | undefined | null,
  level: PrivacyLevel,
): string {
  if (!dob) return "—";
  if (level === "FULL") return dob;

  const birth = new Date(dob);
  if (isNaN(birth.getTime())) return "—";

  const age = Math.floor(
    (Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000),
  );

  if (level === "PARTIAL") return `${age}y`;

  // MINIMAL — decade range
  const decade = Math.floor(age / 10) * 10;
  return `${decade}–${decade + 9}y`;
}

/**
 * Get age from DOB, or null. Always safe to display (not PII by itself
 * when shown as an integer).
 */
export function safeAge(dob: string | undefined | null): number | null {
  if (!dob) return null;
  const birth = new Date(dob);
  if (isNaN(birth.getTime())) return null;
  return Math.floor(
    (Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000),
  );
}

// ── Identifier masking ──────────────────────────────────────────

/**
 * Mask an identifier (national ID, MRN, Health ID, etc.).
 *
 * FULL:    "63-123456-A-78"
 * PARTIAL: "63-••••56-A-78"
 * MINIMAL: "••••••••"
 */
export function maskId(
  id: string | undefined | null,
  level: PrivacyLevel,
): string {
  if (!id) return "—";
  if (level === "FULL") return id;
  if (level === "MINIMAL") return "••••••••";

  // PARTIAL — show first 2 and last 4 chars, mask the middle
  if (id.length <= 6) return `${id[0]}${"•".repeat(id.length - 2)}${id[id.length - 1]}`;
  return `${id.slice(0, 3)}${"•".repeat(id.length - 7)}${id.slice(-4)}`;
}

/**
 * CPID is a clinical pseudonym — safe to display at all levels
 * per Health OS doctrine (it is not PII).
 */
export function displayCpid(cpid: string | undefined | null): string {
  return cpid || "—";
}

// ── Phone masking ───────────────────────────────────────────────

/**
 * Mask a phone number.
 *
 * FULL:    "+263 77 123 4567"
 * PARTIAL: "+263 •••• 4567"
 * MINIMAL: "••••••••••"
 */
export function maskPhone(
  phone: string | undefined | null,
  level: PrivacyLevel,
): string {
  if (!phone) return "—";
  if (level === "FULL") return phone;
  if (level === "MINIMAL") return "••••••••••";

  // PARTIAL — show last 4 digits
  const digits = phone.replace(/\D/g, "");
  if (digits.length <= 4) return "•".repeat(digits.length);
  return `${"•".repeat(digits.length - 4)} ${digits.slice(-4)}`;
}

// ── Email masking ───────────────────────────────────────────────

/**
 * Mask an email address.
 *
 * FULL:    "tariro@example.com"
 * PARTIAL: "t••••o@example.com"
 * MINIMAL: "••••@••••"
 */
export function maskEmail(
  email: string | undefined | null,
  level: PrivacyLevel,
): string {
  if (!email) return "—";
  if (level === "FULL") return email;
  if (level === "MINIMAL") return "••••@••••";

  const [local, domain] = email.split("@");
  if (!domain) return "••••";
  if (local.length <= 2) return `${local[0]}••@${domain}`;
  return `${local[0]}${"•".repeat(local.length - 2)}${local[local.length - 1]}@${domain}`;
}

// ── Gender ──────────────────────────────────────────────────────

/** Gender is clinical context, safe at all levels. */
export function displayGender(gender: string | undefined | null): string {
  if (!gender) return "—";
  return gender;
}

/** Gender single-char badge — safe at all levels. */
export function genderChar(gender: string | undefined | null): string {
  if (!gender) return "—";
  if (gender.toLowerCase() === "female") return "F";
  if (gender.toLowerCase() === "male") return "M";
  return "O";
}

// ── Privacy level label ─────────────────────────────────────────

export const PRIVACY_LEVEL_LABELS: Record<PrivacyLevel, string> = {
  FULL: "Full",
  PARTIAL: "Partial",
  MINIMAL: "Minimal",
};

export const PRIVACY_LEVEL_DESCRIPTIONS: Record<PrivacyLevel, string> = {
  FULL: "All patient identifiers visible",
  PARTIAL: "Names visible, IDs partially masked",
  MINIMAL: "Initials and CPID only",
};
