/**
 * Wellness DEPTH service — Simba plans/journeys, habit check-ins, and wellness-to-care
 * routing for the citizen mobile app. Backed by the Simba sovereign (port 8125) via the
 * Experience BFF: the wellness CRUD is reverse-proxied at /internal/v1/wellness/**, and the
 * care-routing orchestration lives at /internal/v1/wellness/home/care-routing.
 *
 * NOTE: the mobile vitest runner is not executable in this environment (apps/mobile uses pnpm
 * workspace:* protocol; npm install fails with EUNSUPPORTEDPROTOCOL). This module is written and
 * type-checked against the shared @impilo/mobile-api-client export surface, mirroring the existing
 * wellnessService.ts; its tests were NOT run here (documented honest partial).
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/wellness";

type Row = Record<string, unknown>;

export interface WellnessPlan {
  planId: string;
  name: string;
  description: string;
  category: string;
  durationDays?: number;
  clinicalCaution: boolean;
}

export interface PlanEnrollment {
  enrollmentId: string;
  planId: string;
  status: string;
  progressPct: number;
}

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}
function num(v: unknown, fallback = 0): number {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isNaN(n) ? fallback : n;
}

function mapPlan(row: Row): WellnessPlan {
  return {
    planId: str(row.planId ?? row.plan_id),
    name: str(row.name, "Wellness journey"),
    description: str(row.description),
    category: str(row.category, "WELLNESS"),
    durationDays: row.durationDays != null || row.duration_days != null ? num(row.durationDays ?? row.duration_days) : undefined,
    clinicalCaution: Boolean(row.clinicalCaution ?? row.clinical_caution),
  };
}

function mapEnrollment(row: Row): PlanEnrollment {
  return {
    enrollmentId: str(row.enrollmentId ?? row.enrollment_id),
    planId: str(row.planId ?? row.plan_id),
    status: str(row.status, "ACTIVE"),
    progressPct: num(row.progressPct ?? row.progress_pct),
  };
}

/**
 * The Simba wellness endpoints return raw JSON arrays (proxied transparently by the BFF), while
 * the BFF-composed endpoints wrap their payload in `{ data: ... }`. Normalise both shapes.
 */
function rowsOf(body: unknown): Row[] {
  if (Array.isArray(body)) return body as Row[];
  const inner = (body as { data?: unknown })?.data;
  return Array.isArray(inner) ? (inner as Row[]) : [];
}

export async function fetchPlans(): Promise<WellnessPlan[]> {
  const res = await apiClient.get<unknown>(`${V1}/programs`);
  return rowsOf(res.data).map(mapPlan);
}

export async function fetchEnrollments(patientId: string): Promise<PlanEnrollment[]> {
  const res = await apiClient.get<unknown>(
    `${V1}/enrollments?person_cpid=${encodeURIComponent(patientId)}`,
  );
  return rowsOf(res.data).map(mapEnrollment);
}

export async function enrollPlan(planId: string, patientId: string): Promise<void> {
  await apiClient.post(`${V1}/enrollments`, { plan_id: planId, person_cpid: patientId });
}

export async function checkInHabit(patientId: string, habitCode: string): Promise<void> {
  await apiClient.post(`${V1}/habits/check-ins`, {
    person_cpid: patientId,
    habit_code: habitCode,
    status: "DONE",
  });
}

export async function routeToCare(
  patientId: string,
  trigger: string,
  severity: string,
  reason?: string,
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<{ data?: Record<string, unknown> }>(`${V1}/home/care-routing`, {
    person_cpid: patientId,
    trigger,
    severity,
    reason,
  });
  return (res.data?.data ?? res.data ?? {}) as Record<string, unknown>;
}
