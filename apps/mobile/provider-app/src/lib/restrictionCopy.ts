/**
 * Plain-language copy for work-context restriction codes (mirrors web).
 */
const RESTRICTION_COPY: Record<string, string> = {
  PROGRAMME_SUSPENDED:
    "This programme is suspended — it does not confer live authority until active again.",
  PROGRAMME_UNRESOLVED:
    "This programme could not be verified. Treat it as unproven until corrected.",
  PROGRAMME_STATUS_UNVERIFIED:
    "Programme status could not be checked right now.",
  PROGRAMME_CLOSED: "This programme is closed and no longer confers work authority.",
  PROGRAMME_ARCHIVED: "This programme is archived and no longer confers work authority.",
  LEAVE_ACTIVE: "You are recorded as on leave for this assignment.",
};

export function describeWorkContextRestriction(code: string): string {
  return RESTRICTION_COPY[code] ?? code.replace(/_/g, " ").toLowerCase();
}

export function describeWorkContextRestrictions(codes: string[] | null | undefined): string[] {
  if (!codes?.length) return [];
  return codes.map(describeWorkContextRestriction);
}
