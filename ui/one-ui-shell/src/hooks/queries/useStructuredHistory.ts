/**
 * Patient structured history — BFF /internal/v1/ehr/* (Flyway V32).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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

// pct's own projection keys these `distinguisher` (deliberately not a name — see
// StructuredHistoryController: "a relative is a third party with no record here") and
// `conditionLabel`. Mapped to the UI's `name`/`condition` fields here so the page does not read
// `undefined` for every family member and every condition it lists.
function mapFamilyConditionRow(row: Record<string, unknown>): FamilyCondition {
  return {
    id: String(row.id ?? ""),
    condition: String(row.conditionLabel ?? row.condition ?? ""),
    onsetAge: row.onsetAge == null ? null : Number(row.onsetAge),
    status: String(row.status ?? ""),
    notes: String(row.notes ?? ""),
  };
}

function mapFamilyMemberRow(row: Record<string, unknown>): FamilyMember {
  const conditions = Array.isArray(row.conditions)
    ? (row.conditions as Record<string, unknown>[]).map(mapFamilyConditionRow)
    : [];
  return {
    id: String(row.id ?? ""),
    name: String(row.distinguisher ?? row.name ?? ""),
    relationship: String(row.relationship ?? ""),
    age: row.age == null ? null : Number(row.age),
    deceased: Boolean(row.deceased),
    deceasedAge: row.deceasedAge == null ? null : Number(row.deceasedAge),
    causeOfDeath: row.causeOfDeath == null ? null : String(row.causeOfDeath),
    conditions,
  };
}

// pct's own projection keys these `directiveType`/`effectiveFrom`/`reviewedOn`/`documentId`;
// mapped here to the UI's `type`/`effectiveDate`/`reviewDate`/`documentRef` fields. There is no
// contact/contactRelation/contactPhone anywhere in AdvanceDirectiveEntity — nothing in the estate
// captures an emergency contact against a directive — so those stay null rather than fabricated.
function mapAdvanceDirectiveRow(row: Record<string, unknown>): AdvanceDirective {
  return {
    id: String(row.id ?? ""),
    type: String(row.directiveType ?? row.type ?? ""),
    status: String(row.status ?? ""),
    effectiveDate: row.effectiveFrom == null ? null : String(row.effectiveFrom),
    reviewDate: row.reviewedOn == null ? null : String(row.reviewedOn),
    documentRef: row.documentId == null ? null : String(row.documentId),
    summary: String(row.summary ?? ""),
    contact: null,
    contactRelation: null,
    contactPhone: null,
  };
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
  const rawType = String(row.type ?? row.assessmentType ?? row.assessment_type ?? "").toLowerCase();
  const type = (["barthel", "katz", "lawton"].includes(rawType) ? rawType : "barthel") as AssessmentType;
  return {
    id: String(row.id ?? row.assessment_id ?? ""),
    type,
    date: String(row.date ?? row.assessmentDate ?? row.assessment_date ?? ""),
    assessor: String(row.assessor ?? ""),
    totalScore: Number(row.totalScore ?? row.total_score ?? 0),
    maxScore: Number(row.maxScore ?? row.max_score ?? 0),
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
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/ehr/family-history?patient_id=${encodeURIComponent(patientId)}`
      );
      const rows = res.data ?? [];
      return { ...res, data: rows.map(mapFamilyMemberRow) } as ApiResponse<FamilyMember[]>;
    },
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
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/ehr/advance-directives?patient_id=${encodeURIComponent(patientId)}`
      );
      const rows = res.data ?? [];
      return { ...res, data: rows.map(mapAdvanceDirectiveRow) } as ApiResponse<AdvanceDirective[]>;
    },
    enabled: !!patientId,
  });
}

// ── Writes (history-writes) ──────────────────────────────────────────────
//
// pct's own StructuredHistoryController has no PUT/PATCH for any of these five records — each
// POST creates a new row (see FunctionalAssessmentEntity's own "score OR a reason, never neither"
// convention for why an append-only history is the deliberate shape here, not an oversight). A
// "Save" on an existing entry therefore records a new entry rather than mutating the old one; both
// remain visible, newest first, exactly as pct's own `ORDER BY created_at DESC` reads return them.

export interface RecordSocialHistoryInput {
  category: string;
  status: string;
  detail?: string;
  /** Omit when not assessed — never invent LOW. */
  riskLevel?: "LOW" | "MODERATE" | "HIGH";
}

export function useRecordSocialHistory(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordSocialHistoryInput) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/ehr/social-history", {
        subject_cpid: patientId,
        category: input.category,
        status: input.status,
        detail: input.detail,
        risk_level: input.riskLevel,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["ehr", "social-history", patientId] });
    },
  });
}

export interface RecordFamilyMemberInput {
  relationship: string;
  distinguisher?: string;
  age?: number;
}

export function useRecordFamilyMember(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordFamilyMemberInput) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/ehr/family-history", {
        subject_cpid: patientId,
        relationship: input.relationship,
        distinguisher: input.distinguisher,
        age: input.age,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["ehr", "family-history", patientId] });
    },
  });
}

export interface RecordAdvanceDirectiveInput {
  directiveType: string;
  summary?: string;
  status?: string;
  effectiveFrom?: string;
}

export function useRecordAdvanceDirective(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordAdvanceDirectiveInput) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/ehr/advance-directives", {
        subject_cpid: patientId,
        directive_type: input.directiveType,
        summary: input.summary,
        status: input.status,
        effective_from: input.effectiveFrom,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["ehr", "advance-directives", patientId] });
    },
  });
}

export interface RecordFunctionalAssessmentInput {
  assessmentType: AssessmentType | string;
  assessmentDate?: string;
  assessor?: string;
  /** XOR with scoreAbsentReason — never both, never neither. */
  totalScore?: number;
  maxScore?: number;
  scoreAbsentReason?: string;
  interpretation?: string;
}

export function useRecordFunctionalAssessment(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordFunctionalAssessmentInput) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/ehr/functional-assessments", {
        subject_cpid: patientId,
        assessment_type: String(input.assessmentType).toUpperCase(),
        assessment_date: input.assessmentDate,
        assessor: input.assessor,
        total_score: input.totalScore,
        max_score: input.maxScore,
        score_absent_reason: input.scoreAbsentReason,
        interpretation: input.interpretation,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["ehr", "functional-assessments", patientId] });
    },
  });
}

export interface RecordPastProcedureInput {
  name: string;
  code?: string;
  codeSystem?: string;
  procedureType?: string;
  procedureDate?: string;
  datePrecision?: "EXACT" | "MONTH" | "YEAR" | "APPROXIMATE";
  performer?: string;
  facility?: string;
  status?: string;
  notes?: string;
}

export function useRecordPastProcedure(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordPastProcedureInput) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/ehr/procedures", {
        subject_cpid: patientId,
        name: input.name,
        code: input.code,
        code_system: input.codeSystem,
        procedure_type: input.procedureType,
        procedure_date: input.procedureDate,
        date_precision: input.datePrecision ?? "APPROXIMATE",
        performer: input.performer,
        facility: input.facility,
        status: input.status,
        notes: input.notes,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["ehr", "patient-procedures", patientId] });
    },
  });
}
