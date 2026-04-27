/**
 * Longitudinal Summary Hooks — Health OS §4 (Longitudinal Records)
 *
 * Queries patient summaries from the Experience BFF, which bridges to
 * BUTANO (HAPI FHIR R4 shared health record) via SummaryProxyController.
 *
 * BUTANO uses CPID (Clinical Pseudonym ID) only — no PII.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ConsentFlag {
  id?: string;
  code?: string;
  label?: string;
  severity?: string;
  source?: string;
  effectiveDate?: string;
  expiryDate?: string | null;
  state?: string;
  detailHref?: string | null;
}

export interface ConsentRecordItem {
  title?: string;
  consentType?: string;
  state?: string;
  requestId?: string;
  at?: string;
  [key: string]: unknown;
}

export interface ConsentSummary {
  criticalFlags: ConsentFlag[];
  advanceDirectives: ConsentRecordItem[];
  treatmentLimitations: ConsentRecordItem[];
  bloodProductRestrictions: ConsentRecordItem[];
  activeConsents: ConsentRecordItem[];
  missingRequiredConsents: ConsentRecordItem[];
  refusals: ConsentRecordItem[];
  withdrawals: ConsentRecordItem[];
  dataSharingRestrictions: ConsentRecordItem[];
  /** Versioned communication/follow-up preference bundle from Mvumo (independent of one-time registration). */
  communicationPreferenceProfile?: {
    hasProfile?: boolean;
    lifecycleState?: string;
    bundleVersion?: number;
    revisionId?: string;
    updatedAt?: string | null;
    sourceChannel?: string | null;
    auditRef?: string | null;
    payload?: Record<string, unknown>;
  };
  communicationPreferences: ConsentRecordItem[];
  proxyGuardianInfo: ConsentRecordItem[];
  capacityStatus?: string | null;
  lastReviewedAt?: string | null;
  requiresReview?: boolean;
  patientRef?: string;
  generatedAt?: string;
}

export interface PatientSummary {
  cpid: string;
  conditions: Array<{ code: string; display: string; clinicalStatus: string; onsetDate?: string }>;
  medications: Array<{ code: string; display: string; status: string; dosage?: string }>;
  allergies: Array<{ substance: string; reaction: string; criticality: string }>;
  immunizations: Array<{ vaccine: string; date: string; status: string }>;
  recentEncounters: Array<{ id: string; type: string; date: string; facility: string; status: string }>;
  consentSummary?: ConsentSummary;
}

export interface IPSBundle {
  resourceType: "Bundle";
  type: "document";
  entry: Array<{ resource: Record<string, unknown> }>;
}

export interface VisitSummary {
  encounterId: string;
  encounterType: string;
  date: string;
  diagnoses: string[];
  procedures: string[];
  medications: string[];
  notes: string;
}

/** Fetch IPS (International Patient Summary) for a patient CPID. */
export function useIPS(cpid: string | undefined) {
  return useQuery({
    queryKey: ["summary", "ips", cpid],
    queryFn: () => apiClient.get<ApiResponse<IPSBundle>>(`/internal/v1/summary/ips/${cpid}`),
    enabled: !!cpid,
  });
}

/** Fetch visit summary for a specific encounter. */
export function useVisitSummary(encounterId: string | undefined) {
  return useQuery({
    queryKey: ["summary", "visit", encounterId],
    queryFn: () => apiClient.get<ApiResponse<VisitSummary>>(`/internal/v1/summary/visit/${encounterId}`),
    enabled: !!encounterId,
  });
}

/** Fetch aggregated patient summary (conditions, meds, allergies, immunizations, Mvumo `consentSummary`). */
export function usePatientSummary(patientId: string | undefined) {
  return useQuery({
    queryKey: ["summary", "patient", patientId],
    queryFn: () => apiClient.get<ApiResponse<PatientSummary>>(`/internal/v1/summary/patient/${patientId}`),
    enabled: !!patientId,
  });
}

/** Same cache as `usePatientSummary` — use for workflow-only consent gating. */
export function usePatientConsentSurface(patientId: string | undefined) {
  const q = usePatientSummary(patientId);
  return {
    ...q,
    consentSummary: q.data?.data.consentSummary,
  };
}
