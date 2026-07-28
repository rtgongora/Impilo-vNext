/**
 * Regulatory UI vocabulary.
 *
 * The nine organisations USED to be listed here as a hardcoded array with their deterministic
 * org-registry UUIDs. That copy has been retired (NCZ-W1B): it was the third in the estate, after
 * varapi V028 and org-registry V007, and the only one that could drift without anything failing —
 * a council renamed, added or retired in the registry would have left the interface confidently
 * displaying the old name. Organisations now come from `useRegulatoryOrganisations`, which reads
 * org-registry through the BFF.
 *
 * What remains here is a label map for the closed appointment-role vocabulary. It is not a second
 * copy of the organisations, and org-registry serves the codes themselves; only the human wording
 * lives here.
 */

/** Human label for an appointment role_code (org_registry_appointment_role vocabulary). */
export const APPOINTMENT_ROLE_LABELS: Record<string, string> = {
  REGISTRAR: "Registrar",
  DEPUTY_REGISTRAR: "Deputy Registrar",
  REGISTRATION_OFFICER: "Registration Officer",
  INSPECTOR: "Inspector",
  CPD_OFFICER: "CPD Officer",
  INVESTIGATIONS_OFFICER: "Investigations Officer",
  COMMITTEE_MEMBER: "Committee Member",
  FINANCE_OFFICER: "Finance Officer",
  RECORDS_OFFICER: "Records Officer",
  LEGAL_OFFICER: "Legal Officer",
  COUNCIL_CEO: "Council CEO",
  HPA_OVERSIGHT_OFFICER: "HPA Oversight Officer",
  HPA_INSPECTORATE_OFFICER: "HPA Inspectorate Officer",
};

export function roleLabel(code: string | null | undefined): string {
  if (!code) return "";
  return APPOINTMENT_ROLE_LABELS[code] ?? code.replace(/_/g, " ").toLowerCase();
}

export interface RegulatoryAppointment {
  id: string;
  organizationId: string;
  roleCode: string;
  jurisdictionCode?: string;
  status: string;
}
