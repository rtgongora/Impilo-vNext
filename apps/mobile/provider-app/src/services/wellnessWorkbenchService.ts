/**
 * Wellness Workbench service — provider mobile access to a client's wellness summary + follow-ups,
 * composed by the Experience BFF WellnessWorkbenchController (/internal/v1/wellness/workbench/**).
 * The BFF composes the Simba sovereign (assessments/follow-ups/care-linkages) and enforces the
 * approved-client gate (coaching assignment + consent) inside Simba's WellnessAccessGuard.
 *
 * NOTE: the mobile vitest runner is not executable in this environment (apps/mobile uses the pnpm
 * workspace:* protocol; npm install fails with EUNSUPPORTEDPROTOCOL). This module is written and
 * type-checked against the shared @impilo/mobile-api-client export surface, mirroring the existing
 * patientService.ts; its tests were NOT run here (documented honest partial).
 */
import { apiClient } from "@impilo/mobile-api-client";

const WB = "/internal/v1/wellness/workbench";

type Row = Record<string, unknown>;

export interface ClientWellnessSummary {
  personCpid: string;
  latestRiskBand?: string;
  followUps: Row[];
  careLinkages: Row[];
  assessments: Row[];
}

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}
function arr(v: unknown): Row[] {
  return Array.isArray(v) ? (v as Row[]) : [];
}

export async function fetchClientSummary(cpid: string): Promise<ClientWellnessSummary> {
  const res = await apiClient.get<{ data?: Record<string, unknown> }>(
    `${WB}/client/${encodeURIComponent(cpid)}/summary`,
  );
  const data = (res.data?.data ?? {}) as Record<string, unknown>;
  const assessments = arr(data.assessments);
  const latest = assessments[0] as Row | undefined;
  return {
    personCpid: cpid,
    latestRiskBand: latest ? str(latest.riskBand ?? latest.risk_band) || undefined : undefined,
    followUps: arr(data.follow_ups),
    careLinkages: arr(data.care_linkages),
    assessments,
  };
}

export async function createFollowUp(
  cpid: string,
  title: string,
  detail?: string,
  actionType = "REVIEW",
  severity = "ROUTINE",
): Promise<void> {
  await apiClient.post(`${WB}/client/${encodeURIComponent(cpid)}/follow-up`, {
    title,
    detail,
    action_type: actionType,
    severity,
  });
}

export async function escalateToCare(cpid: string, reason: string): Promise<void> {
  await apiClient.post(`${WB}/client/${encodeURIComponent(cpid)}/escalate`, {
    reason,
    trigger: "COACH_ESCALATION",
    severity: "URGENT",
  });
}

export async function recordMeasurement(
  cpid: string,
  payload: Record<string, unknown>,
): Promise<void> {
  await apiClient.post(`${WB}/client/${encodeURIComponent(cpid)}/measurement`, payload);
}
