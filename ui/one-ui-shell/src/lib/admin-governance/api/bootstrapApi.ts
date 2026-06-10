import { apiClient } from "@/lib/api-client";
import type { AdminGovernanceActionResponse, AdminGovernanceEnvelope, LookupEnvelope } from "../types";

const BASE = "/internal/v1/bootstrap";

export interface BootstrapStatus {
  bootstrapRequired: boolean;
  bootstrapOpen: boolean;
  bootstrapClosedAt?: string;
  activeNationalAdminExists: boolean;
  recoveryMode: boolean;
  allowedBootstrapMethods: string[];
  friendlyMessage: string;
}

function unwrap<T>(response: AdminGovernanceEnvelope<T>): T {
  return response.data.attributes;
}

export async function getBootstrapStatus(): Promise<BootstrapStatus> {
  const response = await apiClient.get<AdminGovernanceEnvelope<BootstrapStatus>>(`${BASE}/status`);
  return unwrap(response);
}

export async function validateBootstrapToken(body: Record<string, unknown>) {
  const response = await apiClient.post<AdminGovernanceEnvelope<Record<string, unknown>>>(`${BASE}/validate-token`, body);
  return unwrap(response);
}

export async function createFirstBootstrapAdmin(body: Record<string, unknown>) {
  const response = await apiClient.post<AdminGovernanceEnvelope<Record<string, unknown>>>(`${BASE}/first-admin`, body);
  return unwrap(response);
}

export async function activateBootstrapAdmin(body: Record<string, unknown>) {
  const response = await apiClient.post<AdminGovernanceEnvelope<Record<string, unknown>>>(`${BASE}/activate`, body);
  return unwrap(response);
}

export function buildFirstAdminPayload(form: Record<string, string>, bootstrapMethod: string, bootstrapToken?: string) {
  return {
    bootstrapMethod,
    bootstrapToken,
    sovereignOrganisation: {
      organisationCode: "MOHCC-SOVEREIGN",
      legalName: "Ministry of Health and Child Care",
      organisationType: "sovereign_public_owner",
      country: "Zimbabwe",
      trustTier: "tier_6_sovereign_public_operator",
    },
    nominatedPerson: {
      fullName: form.fullName,
      officialEmail: form.officialEmail,
      phone: form.phone,
      title: form.title,
      organisation: form.organisation,
      approvalReference: form.approvalReference,
    },
    requestedRoles: ["national_bootstrap_administrator"],
    approvalReference: form.approvalReference,
    mfaRequired: true,
  };
}
