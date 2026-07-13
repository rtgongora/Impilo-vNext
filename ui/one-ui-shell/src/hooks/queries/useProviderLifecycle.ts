/**
 * Provider lifecycle queries — VARAPI lifecycle summary, applications, eligibility.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface ApplicationResource {
  id: number;
  applicationType: string;
  workflowState: string;
  authorityRoute?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AffiliationResource {
  id: number;
  facilityId: number;
  affiliationType: string;
  roleTitle?: string;
  status: string;
  startDate?: string;
  endDate?: string;
}

export interface QualificationResource {
  id: number;
  title: string;
  institutionName: string;
  verificationStatus: string;
  awardDate?: string;
}

export interface PracticeContextResource {
  id: number;
  contextType: string;
  status: string;
  authorisationBasis?: string;
  endDate?: string;
}

export interface ComplianceActionResource {
  id: number;
  actionType: string;
  description: string;
  status: string;
  dueDate?: string;
}

export interface CertificateResource {
  id: number;
  certificateType: string;
  status: string;
  expiryDate?: string;
}

export interface PicAssignmentResource {
  id: number;
  facilityId: number;
  assignmentType: string;
  status: string;
  startDate?: string;
}

export interface EligibilityResource {
  eligible: boolean;
  eligibilityStatus: string;
  hasValidLicense: boolean;
  hasActivePractisingCertificate: boolean;
  hasNoOverdueCompliance: boolean;
  hasActiveAffiliation: boolean;
  canServeAsPic: boolean;
  licenseExpiryDate?: string;
  certificateExpiryDate?: string;
  blockReasons?: string[];
}

export interface ProviderLifecycleSummaryResource {
  providerId: number;
  applications: ApplicationResource[];
  affiliations: AffiliationResource[];
  qualifications: QualificationResource[];
  practiceContexts: PracticeContextResource[];
  outstandingCompliance: ComplianceActionResource[];
  currentCertificate?: CertificateResource;
  picAssignment?: PicAssignmentResource;
  eligibility?: EligibilityResource;
}

export function useProviderLifecycleSummary(providerId: string | number) {
  return useQuery({
    queryKey: ["provider-lifecycle", providerId],
    queryFn: async () => {
      return apiClient.get<ProviderLifecycleSummaryResource>(
        `/internal/v1/providers/lifecycle/provider/${providerId}/summary`
      );
    },
    enabled: !!providerId,
    staleTime: 5 * 60 * 1000,
  });
}

export function useProviderEligibility(providerId: string | number, facilityId?: string | number) {
  return useQuery({
    queryKey: ["provider-eligibility", providerId, facilityId],
    queryFn: async () => {
      const url = facilityId
        ? `/internal/v1/interop/eligibility/provider/${providerId}/facility/${facilityId}`
        : `/internal/v1/interop/eligibility/provider/${providerId}`;
      return apiClient.get<EligibilityResource>(url);
    },
    enabled: !!providerId,
    staleTime: 5 * 60 * 1000,
  });
}

export function usePicEligibility(providerId: string | number, facilityId: string | number) {
  return useQuery({
    queryKey: ["pic-eligibility", providerId, facilityId],
    queryFn: async () => {
      return apiClient.get<EligibilityResource>(
        `/internal/v1/interop/eligibility/provider/${providerId}/facility/${facilityId}/pic-eligible`
      );
    },
    enabled: !!providerId && !!facilityId,
    staleTime: 5 * 60 * 1000,
  });
}

// ── Canonical lifecycle transition console (G10) ────────────────────────────

export interface AllowedTransitions {
  current: string;
  allowed: string[];
}

interface TransitionsEnvelope {
  data: AllowedTransitions | { attributes?: AllowedTransitions };
}

function normalizeTransitions(payload: TransitionsEnvelope["data"]): AllowedTransitions {
  if (payload && "current" in payload) return payload as AllowedTransitions;
  if (payload && "attributes" in payload && payload.attributes) return payload.attributes;
  return { current: "", allowed: [] };
}

const lifecycleBase = (publicId: string) =>
  `/internal/v1/registry/providers/${encodeURIComponent(publicId)}/lifecycle`;

export function useLifecycleTransitions(providerPublicId?: string) {
  return useQuery<AllowedTransitions>({
    queryKey: ["provider-lifecycle-transitions", providerPublicId ?? null],
    queryFn: async () => {
      const res = await apiClient.get<TransitionsEnvelope>(`${lifecycleBase(providerPublicId!)}/transitions`);
      return normalizeTransitions(res.data);
    },
    enabled: !!providerPublicId,
    staleTime: 30_000,
  });
}

export interface TransitionInput {
  targetState: string;
  reason?: string;
  effectiveDate?: string;
  sourceRef?: string;
}

export function useTransitionLifecycle(providerPublicId?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TransitionInput) =>
      apiClient.post(`${lifecycleBase(providerPublicId!)}/transitions`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["provider-lifecycle-transitions", providerPublicId ?? null] });
      qc.invalidateQueries({ queryKey: ["providers", providerPublicId] });
    },
  });
}

export const TERMINAL_LIFECYCLE_STATES = new Set(["RETIRED", "DECEASED", "REMOVED"]);
