/**
 * Wellness ASSESSMENT service — Simba baseline wellness assessment + risk scoring for the citizen
 * mobile app. Backed by the Simba sovereign (port 8125) via the Experience BFF, which reverse-proxies
 * /internal/v1/wellness/** transparently.
 *
 * NOTE: the mobile vitest runner is not executable in this environment (apps/mobile uses the pnpm
 * workspace:* protocol; npm install fails with EUNSUPPORTEDPROTOCOL). This module is written and
 * type-checked against the shared @impilo/mobile-api-client export surface, mirroring the existing
 * wellnessDepthService.ts; its tests were NOT run here (documented honest partial).
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/wellness";

type Row = Record<string, unknown>;

export interface Assessment {
  assessmentId: string;
  status: string;
  currentStep?: string;
  consentCaptured: boolean;
  riskBand?: string;
  riskScore?: number;
}

export interface AssessmentResponseInput {
  section: "LIFESTYLE" | "MEASUREMENT" | "RISK_QUESTION";
  question_code: string;
  value_text?: string | null;
  value_numeric?: number | null;
}

export interface AssessmentResult {
  riskBand: string;
  careLinkageRouted: boolean;
  nextActions: string[];
}

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}
function num(v: unknown): number | undefined {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isNaN(n) ? undefined : n;
}

function mapAssessment(row: Row): Assessment {
  return {
    assessmentId: str(row.assessmentId ?? row.assessment_id),
    status: str(row.status, "DRAFT"),
    currentStep: row.currentStep != null ? str(row.currentStep) : undefined,
    consentCaptured: Boolean(row.consentCaptured ?? row.consent_captured),
    riskBand: row.riskBand != null ? str(row.riskBand) : undefined,
    riskScore: num(row.riskScore ?? row.risk_score),
  };
}

function dataOf(body: unknown): Row {
  const inner = (body as { data?: unknown })?.data;
  return (inner ?? body ?? {}) as Row;
}

export async function startOrResumeAssessment(personCpid: string): Promise<Assessment> {
  const res = await apiClient.post<{ data?: Row }>(`${V1}/assessments`, {
    person_cpid: personCpid,
  });
  return mapAssessment(dataOf(res.data));
}

export async function saveAssessmentStep(
  assessmentId: string,
  step: string,
  consentCaptured: boolean | undefined,
  responses: AssessmentResponseInput[],
): Promise<Assessment> {
  const res = await apiClient.patch<{ data?: Row }>(`${V1}/assessments/${assessmentId}/step`, {
    step,
    consent_captured: consentCaptured,
    responses,
  });
  return mapAssessment(dataOf(res.data));
}

export async function completeAssessment(assessmentId: string): Promise<AssessmentResult> {
  const res = await apiClient.post<{
    risk_band?: string;
    care_linkage_routed?: boolean;
    next_actions?: string[];
  }>(`${V1}/assessments/${assessmentId}/complete`, {});
  const body = res.data ?? {};
  return {
    riskBand: str(body.risk_band, "LOW"),
    careLinkageRouted: Boolean(body.care_linkage_routed),
    nextActions: Array.isArray(body.next_actions) ? body.next_actions.map(String) : [],
  };
}
