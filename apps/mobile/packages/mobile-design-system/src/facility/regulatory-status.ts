/**
 * Facility regulatory status — the safety PREDICATE, deliberately free of JSX.
 *
 * Split from FacilityDisclosure.tsx so that:
 *   1. the rule "is this facility unconfirmed?" can be tested and reused without
 *      pulling React components into the import graph, and
 *   2. non-presentational code (filters, sorting, analytics, provider work-context
 *      binding) can ask the question without depending on the design system's
 *      rendering layer.
 *
 * Mirrors the existing clinical/interpretation-flag.ts convention in this package.
 */

/**
 * The tuso `regulatory_status` value written by the HPA import for the 5,509
 * facilities the Health Professions Authority has registered but which NOBODY has
 * confirmed: services unknown, hours unknown, contact details unverified.
 * Such a facility must never be presented to a citizen as open or operating.
 */
export const UNCONFIRMED_REGULATORY_STATUS = "IMPORTED_PENDING_CONFIGURATION";

/**
 * Location precision tiers for the coordinate programme.
 *   address-precise → real coordinate for THIS building; a distance may be shown
 *   approximate     → suburb centroid: ONE point shared by every facility in that
 *                     suburb (188 sit behind "CITY CENTRE/Harare" alone), so a
 *                     precise km figure would be fabricated — never show one
 *   absent          → no coordinate (current state of the HPA-imported set)
 */
export type LocationPrecision = "address-precise" | "approximate" | "absent";

/**
 * Maps the tuso wire values to display tiers. Kept here rather than in the
 * component so every surface reaches the same conclusion from the same input.
 *
 * Unknown values collapse to "absent" ON PURPOSE: if tuso adds a precision level
 * this client has never heard of, the honest response is to claim no location
 * rather than to guess which tier it resembles and risk drawing a pin we cannot
 * justify. Silence is recoverable; a wrong pin sends someone to the wrong place.
 */
export function toLocationPrecision(wire?: string | null): LocationPrecision {
  switch ((wire ?? "").toUpperCase()) {
    case "ADDRESS":
      return "address-precise";
    case "SUBURB_CENTROID":
      return "approximate";
    default:
      return "absent";
  }
}

/**
 * True only for the known unconfirmed status. Unrecognised statuses return false
 * ON PURPOSE: silently treating unknown states as unconfirmed would put a warning
 * on everything as tuso adds lifecycle states, and a warning shown everywhere is
 * ignored everywhere — which would destroy the signal exactly where it matters.
 * New statuses must be added here consciously.
 */
export function isUnconfirmedFacility(regulatoryStatus?: string | null): boolean {
  return (regulatoryStatus ?? "").toUpperCase() === UNCONFIRMED_REGULATORY_STATUS;
}
