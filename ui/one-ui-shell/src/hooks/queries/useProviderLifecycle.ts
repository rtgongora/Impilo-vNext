/**
 * Provider lifecycle queries — VARAPI lifecycle summary, applications, eligibility.
 */

import { useQuery } from "@tanstack/react-query";
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
