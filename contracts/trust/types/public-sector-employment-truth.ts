/**
 * PublicSectorEmploymentTruth — HSC sovereign public-sector employment/posting truth.
 * Separate from ProviderProfessionalTruth (council/professional registration).
 */

export interface PublicSectorEmploymentTruth {
  employmentRecordId?: string;
  providerWorkerId: string;
  linkedHealthId?: string;
  employerOrganisationId?: string;
  employerOrganisationType?: string;
  employmentAuthority: "health_service_commission";
  employmentStatus: string;
  postId?: string;
  postTitle?: string;
  grade?: string;
  cadre?: string;
  establishmentUnit?: string;
  currentPostingProvince?: string;
  currentPostingDistrict?: string;
  currentPostingFacility?: string;
  currentPostingDepartment?: string;
  appointmentDate?: string;
  transferStatus?: string;
  promotionStatus?: string;
  disciplinaryEmploymentStatus?: string;
  effectiveDate?: string;
  expiryDate?: string;
  sourceAuthority?: string;
  verificationStatus?: string;
  auditMetadata?: Record<string, unknown>;
  provenanceMetadata?: Record<string, unknown>;
}

export interface PublicSectorEmploymentSessionContext {
  publicSectorEmploymentStatus?: string;
  hscEmploymentAuthorityStatus?: string;
  currentPostingProvince?: string;
  currentPostingDistrict?: string;
  currentPostingFacility?: string;
  currentPostingDepartment?: string;
  hscEligibilityFlags?: string[];
  employmentFriendlyResolutionState?: string;
}

export const HSC_EMPLOYMENT_WORK_BLOCKED_STATUSES = new Set([
  "not_employed_public_sector",
  "pending_appointment",
  "suspended_from_employment",
  "dismissed",
  "retired",
  "resigned",
  "deceased",
  "contract_expired",
  "under_disciplinary_review",
]);

export const HSC_EMPLOYMENT_REQUIRES_POSTING_STATUSES = new Set([
  "appointed",
  "transferred_pending_assumption",
]);

export function normalizeEmploymentStatus(status: string | null | undefined): string | undefined {
  if (!status) return undefined;
  return status.trim().toLowerCase().replace(/[\s-]+/g, "_");
}

export function requiresPublicSectorEmployment(employerOrganisationType?: string | null): boolean {
  if (!employerOrganisationType) return false;
  const publicTypes = new Set([
    "sovereign_public_owner",
    "ministry_department",
    "national_directorate",
    "provincial_medical_office",
    "district_medical_office",
    "public_facility",
    "central_hospital",
    "provincial_hospital",
    "district_hospital",
    "general_hospital",
    "rural_health_centre",
    "public_clinic",
    "public_health_office",
  ]);
  return publicTypes.has(employerOrganisationType);
}

export function resolvePublicSectorEmploymentFriendlyState(
  employment?: Partial<PublicSectorEmploymentTruth> | null,
): string | undefined {
  if (!employment?.employmentStatus) return "hsc_employment_not_found";
  const status = normalizeEmploymentStatus(employment.employmentStatus);
  if (!status) return "hsc_employment_not_found";
  if (status === "pending_appointment") return "hsc_appointment_pending";
  if (status === "transferred_pending_assumption") return "hsc_transfer_pending_assumption";
  if (status === "suspended_from_employment") return "hsc_employment_suspended";
  if (status === "dismissed") return "hsc_employment_dismissed";
  if (HSC_EMPLOYMENT_REQUIRES_POSTING_STATUSES.has(status)) {
    if (!employment.currentPostingFacility && !employment.currentPostingDepartment) {
      return "hsc_posting_required";
    }
  }
  if (HSC_EMPLOYMENT_WORK_BLOCKED_STATUSES.has(status)) {
    if (status === "not_employed_public_sector") return "hsc_employment_not_found";
    if (status === "under_disciplinary_review") return "hsc_employment_suspended";
  }
  return undefined;
}

export function publicSectorEmploymentBlocksWork(
  employment?: Partial<PublicSectorEmploymentTruth> | null,
  opts?: {
    employerOrganisationType?: string | null;
    isPublicSectorAssignment?: boolean;
    isHscWorkforceGovernanceAssignment?: boolean;
  },
): string | undefined {
  if (opts?.isHscWorkforceGovernanceAssignment) return undefined;

  const needsHsc =
    opts?.isPublicSectorAssignment ||
    requiresPublicSectorEmployment(opts?.employerOrganisationType) ||
    requiresPublicSectorEmployment(employment?.employerOrganisationType);

  if (!needsHsc) return undefined;
  if (!employment?.employmentStatus) return "hsc_employment_not_found";

  const friendly = resolvePublicSectorEmploymentFriendlyState(employment);
  if (friendly) {
    const status = normalizeEmploymentStatus(employment.employmentStatus);
    if (status === "active" || status === "on_leave" || status === "seconded") {
      if (friendly === "hsc_posting_required") return friendly;
      return undefined;
    }
    return friendly;
  }
  return undefined;
}

export function buildPublicSectorEmploymentSessionContext(
  employment?: Partial<PublicSectorEmploymentTruth> | null,
): PublicSectorEmploymentSessionContext | undefined {
  if (!employment?.employmentStatus) return undefined;
  const flags: string[] = [];
  const status = normalizeEmploymentStatus(employment.employmentStatus);
  if (status === "active") flags.push("public_sector_eligible");
  if (status === "on_leave") flags.push("public_sector_on_leave");
  if (status === "seconded") flags.push("public_sector_seconded");
  if (employment.verificationStatus === "verified") flags.push("hsc_employment_verified");

  return {
    publicSectorEmploymentStatus: employment.employmentStatus,
    hscEmploymentAuthorityStatus: employment.verificationStatus ?? employment.employmentStatus,
    currentPostingProvince: employment.currentPostingProvince,
    currentPostingDistrict: employment.currentPostingDistrict,
    currentPostingFacility: employment.currentPostingFacility,
    currentPostingDepartment: employment.currentPostingDepartment,
    hscEligibilityFlags: flags,
    employmentFriendlyResolutionState: resolvePublicSectorEmploymentFriendlyState(employment),
  };
}
