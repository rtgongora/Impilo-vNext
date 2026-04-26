/**
 * Visible 7-stage telemedicine workflow for Experience surfaces.
 * Maps BFF `stage` (1–7) when present; otherwise derives from session `status`.
 */

export const TELEMEDICINE_WORKFLOW_STAGES = [
  { id: 1, label: "Request / Initiation" },
  { id: 2, label: "Triage & Routing" },
  { id: 3, label: "Scheduling / Acceptance" },
  { id: 4, label: "Pre-Consult Preparation" },
  { id: 5, label: "Live or Async Consultation" },
  { id: 6, label: "Outcome / Care Plan" },
  { id: 7, label: "Closure / Follow-up / Audit" },
] as const;

export type TelemedicineWorkflowInput = {
  status?: string | null;
  /** 1-based stage from teleconsult BFF when available */
  bffStage?: number | null;
};

/** Returns 0-based index into TELEMEDICINE_WORKFLOW_STAGES (0..6). */
export function resolveTelemedicineWorkflowIndex(input: TelemedicineWorkflowInput): number {
  const st = (input.status ?? "").toUpperCase();
  if (typeof input.bffStage === "number" && Number.isFinite(input.bffStage)) {
    const s = Math.round(input.bffStage);
    if (s >= 1 && s <= 7) return s - 1;
  }
  if (st === "CANCELLED" || st === "CLOSED") return 6;
  if (st === "COMPLETED" || st === "RESPONDED") return 5;
  if (st === "IN_PROGRESS" || st === "IN_SESSION" || st === "IN_CALL") return 4;
  if (st === "READY" || st === "PREPARED") return 3;
  if (st === "SCHEDULED" || st === "ACCEPTED") return 2;
  if (st === "TRIAGED" || st === "ROUTED") return 1;
  return 0;
}
