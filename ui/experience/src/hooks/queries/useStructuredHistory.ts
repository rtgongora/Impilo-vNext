/**
 * Patient structured history — BFF /internal/v1/ehr/* (Flyway V32).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface SocialHistoryEntry {
  id: string;
  category: string;
  icon: string;
  status: string;
  detail: string;
  lastUpdated: string;
  riskLevel: string;
}

export interface FamilyCondition {
  id: string;
  condition: string;
  onsetAge: number | null;
  status: string;
  notes: string;
}

export interface FamilyMember {
  id: string;
  name: string;
  relationship: string;
  age: number | null;
  deceased: boolean;
  deceasedAge: number | null;
  causeOfDeath: string | null;
  conditions: FamilyCondition[];
}

export interface ActivityScore {
  activity: string;
  score: number;
  maxScore: number;
  level: string;
}

export type AssessmentType = "barthel" | "katz" | "lawton";

export interface FunctionalAssessment {
  id: string;
  type: AssessmentType;
  date: string;
  assessor: string;
  totalScore: number;
  maxScore: number;
  interpretation: string;
  activities: ActivityScore[];
}

export interface PatientProcedure {
  id: string;
  name: string;
  type: string;
  date: string;
  surgeon: string;
  facility: string;
  status: string;
  notes: string;
}

export interface AdvanceDirective {
  id: string;
  type: string;
  status: string;
  effectiveDate: string | null;
  reviewDate: string | null;
  documentRef: string | null;
  summary: string;
  contact: string | null;
  contactRelation: string | null;
  contactPhone: string | null;
}

function parseActivities(raw: unknown): ActivityScore[] {
  if (raw == null) return [];
  if (typeof raw === "string") {
    try {
      return parseActivities(JSON.parse(raw));
    } catch {
      return [];
    }
  }
  if (!Array.isArray(raw)) return [];
  return raw.map((item) => {
    const o = item as Record<string, unknown>;
    return {
      activity: String(o.activity ?? ""),
      score: Number(o.score ?? 0),
      maxScore: Number(o.maxScore ?? o.max_score ?? 0),
      level: String(o.level ?? ""),
    };
  });
}

function mapFunctionalRow(row: Record<string, unknown>): FunctionalAssessment {
  return {
    id: String(row.id),
    type: row.type as AssessmentType,
    date: String(row.date ?? ""),
    assessor: String(row.assessor ?? ""),
    totalScore: Number(row.totalScore ?? 0),
    maxScore: Number(row.maxScore ?? 0),
    interpretation: String(row.interpretation ?? ""),
    activities: parseActivities(row.activities),
  };
}

export function useSocialHistory(patientId: string) {
  return useQuery({
    queryKey: ["ehr", "social-history", patientId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<SocialHistoryEntry[]>>(
        `/internal/v1/ehr/social-history?patient_id=${encodeURIComponent(patientId)}`
      );
      return res;
    },
    enabled: !!patientId,
  });
}

export function useFamilyHistory(patientId: string) {
  return useQuery({
    queryKey: ["ehr", "family-history", patientId],
    queryFn: () =>
      apiClient.get<ApiResponse<FamilyMember[]>>(
        `/internal/v1/ehr/family-history?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useFunctionalAssessments(patientId: string) {
  return useQuery({
    queryKey: ["ehr", "functional-assessments", patientId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/ehr/functional-assessments?patient_id=${encodeURIComponent(patientId)}`
      );
      const rows = res.data ?? [];
      return {
        ...res,
        data: rows.map(mapFunctionalRow),
      } as ApiResponse<FunctionalAssessment[]>;
    },
    enabled: !!patientId,
  });
}

export function usePatientProcedures(patientId: string) {
  return useQuery({
    queryKey: ["ehr", "patient-procedures", patientId],
    queryFn: () =>
      apiClient.get<ApiResponse<PatientProcedure[]>>(
        `/internal/v1/ehr/procedures?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useAdvanceDirectives(patientId: string) {
  return useQuery({
    queryKey: ["ehr", "advance-directives", patientId],
    queryFn: () =>
      apiClient.get<ApiResponse<AdvanceDirective[]>>(
        `/internal/v1/ehr/advance-directives?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}
