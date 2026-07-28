/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Shared helpers for the surgical region maps.
 *
 * Two ways to bind a region to terminology, and the difference is deliberate:
 *
 *   code()    — a SNOMED CT body-structure concept the author could name with confidence.
 *   pending() — the concept exists in SNOMED CT but the author could not source the exact
 *               identifier. Rather than write a plausible-looking wrong code (which no test
 *               can catch and which silently corrupts every downstream count), the binding is
 *               written as a loudly invalid placeholder that fails terminology validation and
 *               is greppable by `isPendingTerminologyBinding`.
 *
 * A wrong code is worse than a missing one: a missing code announces itself, a wrong code
 * reports the wrong anatomy forever. Every `pending` binding is an open item for the clinical
 * terminology review that ratifies these maps.
 */

import { SNOMED_BODY_STRUCTURE, type BodyRegionDef } from "../core/types";

/** A SNOMED CT body-structure concept the author could name with confidence. */
export const code = (id: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: id,
  display,
});

/**
 * Marker prefix for a location whose SNOMED CT identifier has not been sourced.
 *
 * Kept inside the SNOMED system on purpose: the location *is* a SNOMED concept, and pretending
 * it belongs to some private code system would hide the gap in a place nobody reviews. The
 * prefix makes the value fail any real terminology validation, which is the intended behaviour.
 */
export const PENDING_SNOMED_PREFIX = "PENDING-SNOMED:";

/** A location whose SNOMED CT identifier is not yet verified. See `PENDING_SNOMED_PREFIX`. */
export const pending = (slug: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: `${PENDING_SNOMED_PREFIX}${slug}`,
  display: `${display} (SNOMED binding not verified)`,
});

/** True when a region's code is a placeholder awaiting terminology review. */
export function isPendingTerminologyBinding(locationCode: {
  system: string;
  code: string;
  display?: string;
}): boolean {
  return locationCode.code.startsWith(PENDING_SNOMED_PREFIX);
}

/**
 * How much of a map is really coded, for the coverage register and for any guard that refuses
 * to let an unverified map be used where a coded location is mandatory.
 */
export function terminologyCoverage(regions: BodyRegionDef[]): {
  total: number;
  coded: number;
  pending: number;
} {
  const pendingCount = regions.filter((region) =>
    isPendingTerminologyBinding(region.locationCode),
  ).length;
  return {
    total: regions.length,
    coded: regions.length - pendingCount,
    pending: pendingCount,
  };
}
