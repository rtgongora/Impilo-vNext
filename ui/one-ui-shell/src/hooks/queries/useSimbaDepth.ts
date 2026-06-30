/**
 * Experience UI — SIMBA wellness DEPTH hooks (plans/journeys, habit check-ins,
 * coaching relationships, wellness-to-care linkage, wellness home overview).
 *
 * All endpoints are served by the Simba sovereign (port 8125) via the experience-bff:
 * the wellness CRUD is reverse-proxied at /internal/v1/wellness/**, and the composite
 * overview + care-routing orchestration live at /internal/v1/wellness/home/**.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type ListResponse = ApiResponse<unknown>;

function withCpid(path: string, cpid?: string | null) {
  const sp = new URLSearchParams();
  if (cpid) sp.set("person_cpid", cpid);
  const qs = sp.toString();
  return `${path}${qs ? `?${qs}` : ""}`;
}

// ── Wellness home overview (composite) ──────────────────────────────────────
export function useWellnessHome(enabled = true) {
  return useQuery<ApiResponse<Record<string, unknown>>>({
    queryKey: ["wellness-home-overview"],
    queryFn: () => apiClient.get<ApiResponse<Record<string, unknown>>>("/internal/v1/wellness/home/overview"),
    enabled,
  });
}

// ── Plans / journeys ────────────────────────────────────────────────────────
export function useWellnessPlans() {
  return useQuery<ListResponse>({
    queryKey: ["wellness-plans"],
    queryFn: () => apiClient.get<ListResponse>("/internal/v1/wellness/programs"),
  });
}

export function useWellnessEnrollments(cpid?: string | null) {
  return useQuery<ListResponse>({
    queryKey: ["wellness-enrollments", cpid ?? null],
    queryFn: () => apiClient.get<ListResponse>(withCpid("/internal/v1/wellness/enrollments", cpid)),
    enabled: !!cpid,
  });
}

export function useEnrollmentTasks(enrollmentId?: string | null) {
  return useQuery<ListResponse>({
    queryKey: ["wellness-enrollment-tasks", enrollmentId ?? null],
    queryFn: () => apiClient.get<ListResponse>(`/internal/v1/wellness/enrollments/${enrollmentId}/tasks`),
    enabled: !!enrollmentId,
  });
}

export function useEnrollPlan() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { plan_id: string; person_cpid: string }>({
    mutationFn: (body) => apiClient.post<ApiResponse<unknown>>("/internal/v1/wellness/enrollments", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["wellness-enrollments"] });
      void qc.invalidateQueries({ queryKey: ["wellness-home-overview"] });
    },
  });
}

export function useCompleteEnrollmentTask() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, string>({
    mutationFn: (enrollmentTaskId) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/wellness/enrollments/tasks/${enrollmentTaskId}/complete`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["wellness-enrollment-tasks"] });
      void qc.invalidateQueries({ queryKey: ["wellness-enrollments"] });
    },
  });
}

// ── Habit check-ins ─────────────────────────────────────────────────────────
export function useHabitCheckIns(cpid?: string | null) {
  return useQuery<ListResponse>({
    queryKey: ["wellness-habit-checkins", cpid ?? null],
    queryFn: () => apiClient.get<ListResponse>(withCpid("/internal/v1/wellness/habits/check-ins", cpid)),
    enabled: !!cpid,
  });
}

export function useCheckInHabit() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<ApiResponse<unknown>>("/internal/v1/wellness/habits/check-ins", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["wellness-habit-checkins"] });
    },
  });
}

// ── Coaching ────────────────────────────────────────────────────────────────
export function useCoachingRelationships(cpid?: string | null) {
  return useQuery<ListResponse>({
    queryKey: ["wellness-coaching", cpid ?? null],
    queryFn: () =>
      apiClient.get<ListResponse>(withCpid("/internal/v1/wellness/coaching/relationships", cpid)),
    enabled: !!cpid,
  });
}

// ── Wellness-to-care linkage ────────────────────────────────────────────────
export function useCareLinkages(cpid?: string | null) {
  return useQuery<ListResponse>({
    queryKey: ["wellness-care-linkages", cpid ?? null],
    queryFn: () => apiClient.get<ListResponse>(withCpid("/internal/v1/wellness/care-linkages", cpid)),
    enabled: !!cpid,
  });
}

export function useRouteToCare() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<ApiResponse<unknown>>("/internal/v1/wellness/home/care-routing", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["wellness-care-linkages"] });
      void qc.invalidateQueries({ queryKey: ["wellness-home-overview"] });
    },
  });
}
