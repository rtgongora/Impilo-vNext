/**
 * The nine regulatory organisations (ROM). Deterministic org-registry UUIDs
 * (a5000000-0000-4000-8000-00000000000N) — the same constants seeded in
 * org-registry V007 + varapi V028. Used to render friendly names in the
 * regulatory workspace picker without a round-trip.
 */
export interface RegulatoryOrg {
  id: string;
  code: string;
  name: string;
  kind: "authority" | "council";
}

export const REGULATORY_ORGS: RegulatoryOrg[] = [
  { id: "a5000000-0000-4000-8000-000000000001", code: "HPA", name: "Health Professions Authority", kind: "authority" },
  { id: "a5000000-0000-4000-8000-000000000002", code: "MDPCZ", name: "Medical and Dental Practitioners Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000003", code: "NCZ", name: "Nurses Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000004", code: "PCZ", name: "Pharmacists Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000005", code: "AHPCZ", name: "Allied Health Practitioners Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000006", code: "EHPCZ", name: "Environmental Health Practitioners Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000007", code: "MRPCZ", name: "Medical Rehabilitation Practitioners Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000008", code: "MLCSCZ", name: "Medical Laboratory and Clinical Scientists Council of Zimbabwe", kind: "council" },
  { id: "a5000000-0000-4000-8000-000000000009", code: "NTCZ", name: "Natural Therapists Council of Zimbabwe", kind: "council" },
];

const BY_ID = new Map(REGULATORY_ORGS.map((o) => [o.id.toLowerCase(), o]));

export function regulatoryOrg(id: string | null | undefined): RegulatoryOrg | undefined {
  return id ? BY_ID.get(id.toLowerCase()) : undefined;
}

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
