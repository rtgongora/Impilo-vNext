/**
 * VARAPI ProviderProfessionalTruth — sovereign professional/workforce truth.
 */

export interface QualificationRecord {
  code: string;
  displayName?: string;
  institution?: string;
  yearAwarded?: number;
  verified?: boolean;
}

export interface CpdComplianceSummary {
  status: string;
  pointsCurrent?: number;
  mandatoryTrainingComplete?: boolean;
  requiredCertificateValid?: boolean;
  trainingGaps?: string[];
  lastComplianceUpdate?: string;
}

export interface ProviderProfessionalTruth {
  providerWorkerId: string;
  linkedHealthId?: string;
  identityType?: string;
  providerWorkerType?: string;
  profession?: string;
  cadre?: string;
  qualifications?: QualificationRecord[];
  specialities?: string[];
  certifications?: string[];
  registrationBody?: string;
  registrationNumber?: string;
  licenceStatus?: string;
  certificationStatus?: string;
  scopeOfPractice?: string[];
  restrictions?: string[];
  sanctions?: string[];
  suspensions?: string[];
  expiryDates?: Record<string, string>;
  competenceSkillsProfile?: Record<string, unknown>;
  cpdComplianceSummary?: CpdComplianceSummary;
  providerWorkerStatus: string;
  approvedWorkContextEligibilitySummary?: string[];
  sourceRegistry?: string;
  verificationEvidence?: Record<string, unknown>;
  auditMetadata?: Record<string, unknown>;
}

export const PROFESSIONAL_ELIGIBLE_STATUSES = new Set([
  "verified",
  "active",
  "active_restricted",
]);

export const WORK_BLOCKED_PROVIDER_STATUSES = new Set([
  "not_started",
  "draft",
  "submitted",
  "pending_verification",
  "suspended",
  "expired",
  "revoked",
  "retired",
  "deceased",
  "duplicate_under_review",
  "requires_update",
  "under_investigation",
]);

export function providerStatusAllowsProfessional(status: string | null | undefined): boolean {
  if (!status) return false;
  const normalized = status.toLowerCase().replace(/[\s-]+/g, "_");
  return PROFESSIONAL_ELIGIBLE_STATUSES.has(normalized);
}

export function providerStatusBlocksWork(status: string | null | undefined): boolean {
  if (!status) return true;
  const normalized = status.toLowerCase().replace(/[\s-]+/g, "_");
  if (normalized === "active_restricted") return false;
  return WORK_BLOCKED_PROVIDER_STATUSES.has(normalized);
}
