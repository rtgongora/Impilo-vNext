/**
 * Programme indicator hooks — map structured form answers to indicator numerator/denominator codes.
 * Reporting plane consumes these codes via analytics pipeline (future).
 */

export const ANC_INDICATORS = {
  ANC1_CONTACT: "ANC.CONTACT1.completed",
  SYPHILIS_SCREEN_DONE: "ANC.SYPHILIS.screened",
  HIV_STATUS_DOCUMENTED: "ANC.HIV.status_documented",
  PARTNER_INVOLVEMENT_DOCUMENTED: "ANC.PARTNER.documented",
} as const;
