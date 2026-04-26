/**
 * Care plans from BFF — GET /internal/v1/care-plans?patientId= (V27 care_plans + nested goals/interventions).
 */

import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** UI shape for care-plans page */
export interface CarePlanGoalUi {
  id: string;
  description: string;
  progress: number;
  category: string;
  targetDate: string;
  createdDate: string;
  statusKey: string;
  priorityKey: string;
  notes: string;
}

export interface CarePlanUi {
  id: string;
  title: string;
  status: "Active" | "Completed" | "Draft";
  category: string;
  startDate: string;
  targetDate: string;
  author: string;
  goals: CarePlanGoalUi[];
  interventions: { id: string; label: string; completed: boolean }[];
}

function isoDate(value: unknown): string {
  if (value == null) return "";
  const s = String(value);
  if (s.length >= 10) return s.slice(0, 10);
  return s;
}

function mapPlanStatus(raw: unknown): CarePlanUi["status"] {
  const u = String(raw ?? "ACTIVE").toUpperCase();
  if (u === "COMPLETED" || u === "CLOSED") return "Completed";
  if (u === "DRAFT") return "Draft";
  return "Active";
}

function goalProgress(row: Record<string, unknown>): number {
  const st = String(row.status ?? "IN_PROGRESS").toUpperCase();
  if (st === "ACHIEVED") return 100;
  if (st === "CANCELLED" || st === "ABANDONED") return 0;
  const cur = row.current_value;
  if (typeof cur === "string" && /^\d+(\.\d+)?$/.test(cur.trim())) {
    const n = Number(cur);
    if (!Number.isNaN(n)) return Math.min(100, Math.round(n));
  }
  if (typeof cur === "string" && cur.includes("%")) {
    const m = cur.match(/(\d+)/);
    if (m) return Math.min(100, parseInt(m[1], 10));
  }
  return st === "IN_PROGRESS" ? 0 : 0;
}

function interventionCompleted(row: Record<string, unknown>): boolean {
  return row.last_performed != null || String(row.status ?? "").toUpperCase() === "COMPLETED";
}

export function mapCarePlanRow(raw: Record<string, unknown>): CarePlanUi {
  const goalsRaw = (raw.goals as Record<string, unknown>[]) ?? [];
  const intRaw = (raw.interventions as Record<string, unknown>[]) ?? [];

  const goalDates = goalsRaw.map((g) => isoDate(g.target_date)).filter(Boolean);
  const targetDate = goalDates.length > 0 ? goalDates.sort().slice(-1)[0]! : "";

  return {
    id: String(raw.id ?? ""),
    title: String(raw.title ?? "Care plan"),
    status: mapPlanStatus(raw.status),
    category: String(raw.plan_type ?? "Care"),
    startDate: isoDate(raw.created_at),
    targetDate,
    author: String(raw.created_by ?? "—"),
    goals: goalsRaw.map((g) => ({
      id: String(g.id ?? ""),
      description: String(g.description ?? ""),
      progress: goalProgress(g),
      category: String(g.category ?? "Clinical"),
      targetDate: isoDate(g.target_date),
      createdDate: isoDate(g.created_at),
      statusKey: String(g.status ?? "IN_PROGRESS"),
      priorityKey: String(g.priority ?? "MEDIUM"),
      notes: g.current_value != null && String(g.current_value).length > 0 ? `Current: ${g.current_value}` : "",
    })),
    interventions: intRaw.map((i) => ({
      id: String(i.id ?? ""),
      label: String(i.description ?? ""),
      completed: interventionCompleted(i),
    })),
  };
}

export function useCarePlans(patientId: string) {
  return useQuery({
    queryKey: ["care-plans", "bff", patientId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/care-plans?patientId=${encodeURIComponent(patientId)}`
      );
      const rows = res.data ?? [];
      return {
        ...res,
        data: rows.map((r) => mapCarePlanRow(r)),
      } as ApiResponse<CarePlanUi[]>;
    },
    enabled: !!patientId,
  });
}

/** Goals page — flattened from nested care-plan goals (same BFF source). */
export interface PatientGoalUi {
  id: string;
  title: string;
  description: string;
  status: "On Track" | "At Risk" | "Behind" | "Achieved" | "Cancelled";
  priority: "High" | "Medium" | "Low";
  progress: number;
  targetDate: string;
  createdDate: string;
  linkedCarePlan: string | null;
  category: string;
  notes: string;
}

function mapPriorityKey(p: string): PatientGoalUi["priority"] {
  const u = p.toUpperCase();
  if (u === "HIGH" || u === "URGENT") return "High";
  if (u === "LOW") return "Low";
  return "Medium";
}

function planGoalToPatientGoal(goal: CarePlanGoalUi, planTitle: string): PatientGoalUi {
  const st = goal.statusKey.toUpperCase();
  let status: PatientGoalUi["status"] = "On Track";
  if (st === "ACHIEVED") status = "Achieved";
  else if (st === "CANCELLED") status = "Cancelled";
  const title =
    goal.description.length > 80 ? `${goal.description.slice(0, 77)}…` : goal.description || "Goal";
  return {
    id: goal.id,
    title,
    description: goal.description,
    status,
    priority: mapPriorityKey(goal.priorityKey),
    progress: goal.progress,
    targetDate: goal.targetDate,
    createdDate: goal.createdDate,
    linkedCarePlan: planTitle,
    category: goal.category,
    notes: goal.notes,
  };
}

export function usePatientGoalsFromCarePlans(patientId: string) {
  const carePlansQuery = useCarePlans(patientId);

  const derived = useMemo(() => {
    const plans = carePlansQuery.data?.data ?? [];
    const list: PatientGoalUi[] = [];
    for (const plan of plans) {
      for (const g of plan.goals) {
        list.push(planGoalToPatientGoal(g, plan.title));
      }
    }
    return list;
  }, [carePlansQuery.data?.data]);

  return {
    ...carePlansQuery,
    data: carePlansQuery.data
      ? { ...carePlansQuery.data, data: derived }
      : undefined,
  };
}

// --- Care Team ---

export interface CareTeamMember {
  id: string;
  name: string;
  role: string;
  specialty: string;
  assignedAt: string;
  status: "active" | "inactive";
}

export function useCareTeam(patientId: string) {
  return useQuery<{ data: CareTeamMember[] }>({
    queryKey: ["care-team", patientId],
    queryFn: () => apiClient.get(`/internal/v1/care-team?patientId=${encodeURIComponent(patientId)}`),
    enabled: !!patientId,
  });
}

export function useAddCareTeamMember() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { patientId: string; name: string; role: string; specialty: string }>({
    mutationFn: (payload) => apiClient.post("/internal/v1/care-team", payload),
    onSuccess: (_d, vars) => { qc.invalidateQueries({ queryKey: ["care-team", vars.patientId] }); },
  });
}

export function useRemoveCareTeamMember() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { memberId: string; patientId: string }>({
    mutationFn: ({ memberId }) => apiClient.delete(`/internal/v1/care-team/${memberId}`),
    onSuccess: (_d, vars) => { qc.invalidateQueries({ queryKey: ["care-team", vars.patientId] }); },
  });
}

// --- Create Goal ---

export function useCreateGoal() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { patientId: string; title: string; description: string; priority: string; targetDate: string; carePlanId?: string }>({
    mutationFn: (payload) => apiClient.post("/internal/v1/goals", payload),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: ["care-plans", vars.patientId] });
    },
  });
}

// --- Create Care Plan ---

export function useCreateCarePlan() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { patientId: string; title: string; category: string; targetDate: string; goals?: string[] }>({
    mutationFn: (payload) => apiClient.post("/internal/v1/care-plans", payload),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: ["care-plans", vars.patientId] });
    },
  });
}
