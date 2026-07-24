/**
 * Organization onboarding query hooks (Experience → BFF → organization-registry).
 *
 * Drives the org-onboarding wizard: create organization (DRAFT) → add authorized
 * representatives → submit for verification → national-admin verify.
 *
 * BFF base: /internal/v1/organizations. Responses use the organization-registry
 * envelope { success, requestId, correlationId, data }; hooks read `data`.
 * Network helpers are exported for unit testing against a mocked api-client.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export const ORGANIZATIONS_BASE = "/internal/v1/organizations";

export interface OrgRegistryEnvelope<T> {
  success?: boolean;
  requestId?: string;
  correlationId?: string;
  data: T;
}

export interface Organization {
  id?: string;
  code?: string;
  legalName?: string;
  orgType?: string;
  status?: string;
  countryOperationRef?: string;
  logoObjectId?: string;
  [key: string]: unknown;
}

export interface OrganizationRepresentative {
  id?: string;
  personHealthId?: string;
  roleTitle?: string;
  evidenceRef?: string;
  status?: string;
  validFrom?: string;
  validTo?: string;
  appointedBy?: string;
  [key: string]: unknown;
}

export interface CreateOrganizationBody {
  code: string;
  legalName: string;
  orgType: string;
  countryOperationRef?: string;
  registrationIdentifiers?: Record<string, unknown>;
}

export interface AddRepresentativeVars {
  organizationId: string;
  personHealthId: string;
  roleTitle: string;
  evidenceRef?: string;
  validFrom?: string;
  validTo?: string;
  appointedBy?: string;
}

export interface VerifyOrganizationVars {
  organizationId: string;
  verifiedRepresentativeIds: string[];
}

// ── Network helpers (exported for unit tests) ──────────────────────────────

export function fetchOrganization(id: string) {
  return apiClient.get<OrgRegistryEnvelope<Organization>>(`${ORGANIZATIONS_BASE}/${id}`);
}

export function fetchRepresentatives(id: string) {
  return apiClient.get<OrgRegistryEnvelope<OrganizationRepresentative[]>>(
    `${ORGANIZATIONS_BASE}/${id}/representatives`,
  );
}

export function createOrganization(body: CreateOrganizationBody) {
  return apiClient.post<OrgRegistryEnvelope<Organization>>(ORGANIZATIONS_BASE, body);
}

export function addRepresentative(vars: AddRepresentativeVars) {
  const { organizationId, ...body } = vars;
  return apiClient.post<OrgRegistryEnvelope<OrganizationRepresentative>>(
    `${ORGANIZATIONS_BASE}/${organizationId}/representatives`,
    body,
  );
}

export function submitVerification(organizationId: string) {
  return apiClient.post<OrgRegistryEnvelope<Organization>>(
    `${ORGANIZATIONS_BASE}/${organizationId}/submit-verification`,
  );
}

export function verifyOrganization(vars: VerifyOrganizationVars) {
  return apiClient.post<OrgRegistryEnvelope<Organization>>(
    `${ORGANIZATIONS_BASE}/${vars.organizationId}/verify`,
    { verifiedRepresentativeIds: vars.verifiedRepresentativeIds },
  );
}

export function uploadOrganizationLogo(organizationId: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  return apiClient.postForm<OrgRegistryEnvelope<Organization>>(
    `${ORGANIZATIONS_BASE}/${organizationId}/logo`,
    form,
  );
}

// ── Query hooks ────────────────────────────────────────────────────────────

export function useOrganization(id: string | undefined) {
  return useQuery({
    queryKey: ["org-onboarding", "organization", id],
    queryFn: () => fetchOrganization(id as string),
    enabled: Boolean(id),
  });
}

export function useRepresentatives(id: string | undefined) {
  return useQuery({
    queryKey: ["org-onboarding", "representatives", id],
    queryFn: () => fetchRepresentatives(id as string),
    enabled: Boolean(id),
  });
}

// ── Mutation hooks ─────────────────────────────────────────────────────────

export function useCreateOrganization() {
  return useMutation({ mutationFn: createOrganization });
}

export function useAddRepresentative() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: addRepresentative,
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["org-onboarding", "representatives", vars.organizationId] });
    },
  });
}

export function useSubmitVerification() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (organizationId: string) => submitVerification(organizationId),
    onSuccess: (_res, organizationId) => {
      void qc.invalidateQueries({ queryKey: ["org-onboarding", "organization", organizationId] });
    },
  });
}

export function useVerifyOrganization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: verifyOrganization,
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["org-onboarding", "organization", vars.organizationId] });
    },
  });
}

export function useUploadOrganizationLogo() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ organizationId, file }: { organizationId: string; file: File }) =>
      uploadOrganizationLogo(organizationId, file),
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["org-onboarding", "organization", vars.organizationId] });
    },
  });
}
