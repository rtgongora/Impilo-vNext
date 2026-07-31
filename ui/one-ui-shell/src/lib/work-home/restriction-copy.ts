/**
 * Plain-language copy for work-context restriction codes the BFF emits.
 * Raw codes stay available for support; the UI must not make the person decode them.
 */
const RESTRICTION_COPY: Record<string, string> = {
  PROGRAMME_SUSPENDED:
    "This programme is suspended — you can open the workplace, but it does not confer live authority until it is active again.",
  PROGRAMME_UNRESOLVED:
    "This programme could not be verified in the registry. Treat it as unproven until the appointment is corrected.",
  PROGRAMME_STATUS_UNVERIFIED:
    "Programme status could not be checked right now. Authority may be incomplete until the registry is reachable.",
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

/** Human label for a WorkMode enum name. */
export function describeWorkMode(mode: string | null | undefined): string {
  if (!mode) return "";
  return mode
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}
