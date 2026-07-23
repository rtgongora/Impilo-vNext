/**
 * Wave OF-C — telemonitoring hooks over the BFF proxy
 * (`/internal/v1/telemonitoring/**` → telemonitoring-service, port 8394).
 *
 * Envelope note: telemonitoring-service replies with RAW DTO payloads (no
 * `{success,data}` wrapper) which the BFF forwards verbatim — so these hooks
 * type the payloads directly. Domain rejections arrive as thrown objects with
 * `{status, error:{code,message}}` (the coded-envelope passthrough).
 *
 * Idempotency: the api-client auto-generates an Idempotency-Key per POST; the
 * BFF forwards it to the write lanes.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type {
  AccountableClosure,
  AlertEpisodeView,
  AlertStatus,
  DeviceAssignmentView,
  MonitoringPlanView,
  MonitoringProgrammeView,
  PagedReadingsView,
  ResolvedAssignmentView,
  ThresholdProfileView,
} from "@/lib/telemonitoring/types";

const BASE = "/internal/v1/telemonitoring";

export const telemonitoringKeys = {
  episodes: (status?: AlertStatus | "ALL", planId?: string) =>
    ["tm-alert-episodes", status ?? "ALL", planId ?? null] as const,
  episode: (episodeId: string) => ["tm-alert-episode", episodeId] as const,
  plans: (patientCpid: string) => ["tm-plans", patientCpid] as const,
  myPlans: ["tm-my-plans"] as const,
  plan: (planId: string) => ["tm-plan", planId] as const,
  thresholds: (planId: string) => ["tm-thresholds", planId] as const,
  readingsByPlan: (planId: string, page: number) => ["tm-readings-plan", planId, page] as const,
  assignments: (planId?: string, patientCpid?: string) =>
    ["tm-assignments", planId ?? null, patientCpid ?? null] as const,
  myAssignments: ["tm-my-assignments"] as const,
  resolvedDevice: (deviceRef: string) => ["tm-device-posture", deviceRef] as const,
  programmes: ["tm-programmes"] as const,
};

// ── Alert episodes (OF-B26/OF-B28) ──────────────────────────────────────────

/**
 * Episode listing for the triage board. Polls every 30s — a triage board that
 * silently goes stale hides response-window pressure.
 */
export function useAlertEpisodes(options?: { status?: AlertStatus; planId?: string; enabled?: boolean }) {
  const { status, planId, enabled = true } = options ?? {};
  return useQuery<AlertEpisodeView[]>({
    queryKey: telemonitoringKeys.episodes(status ?? "ALL", planId),
    enabled,
    refetchInterval: 30_000,
    queryFn: () => {
      const params = new URLSearchParams();
      if (status) params.set("status", status);
      if (planId) params.set("planId", planId);
      const qs = params.toString();
      return apiClient.get<AlertEpisodeView[]>(`${BASE}/alert-episodes${qs ? `?${qs}` : ""}`);
    },
  });
}

export function useAlertEpisode(episodeId: string | undefined) {
  return useQuery<AlertEpisodeView>({
    queryKey: telemonitoringKeys.episode(episodeId ?? "unknown"),
    enabled: !!episodeId,
    queryFn: () =>
      apiClient.get<AlertEpisodeView>(`${BASE}/alert-episodes/${encodeURIComponent(episodeId!)}`),
  });
}

function useEpisodeMutation<TVars extends { episodeId: string }>(
  run: (vars: TVars) => Promise<AlertEpisodeView>,
) {
  const queryClient = useQueryClient();
  return useMutation<AlertEpisodeView, unknown, TVars>({
    mutationFn: run,
    onSuccess: (_data, vars) => {
      void queryClient.invalidateQueries({ queryKey: telemonitoringKeys.episode(vars.episodeId) });
      void queryClient.invalidateQueries({ queryKey: ["tm-alert-episodes"] });
    },
  });
}

export function useAcknowledgeEpisode() {
  return useEpisodeMutation<{ episodeId: string }>(({ episodeId }) =>
    apiClient.post<AlertEpisodeView>(
      `${BASE}/alert-episodes/${encodeURIComponent(episodeId)}/acknowledge`,
      {},
    ),
  );
}

export function useStartEpisodeProgress() {
  return useEpisodeMutation<{ episodeId: string }>(({ episodeId }) =>
    apiClient.post<AlertEpisodeView>(
      `${BASE}/alert-episodes/${encodeURIComponent(episodeId)}/start`,
      {},
    ),
  );
}

/** §14.7 — record a ladder rung with a note; actor comes from trust context. */
export function useRecordLadderStep() {
  return useEpisodeMutation<{ episodeId: string; rung: number; note?: string }>(
    ({ episodeId, rung, note }) =>
      apiClient.post<AlertEpisodeView>(
        `${BASE}/alert-episodes/${encodeURIComponent(episodeId)}/ladder-steps`,
        { rung, note: note ?? null },
      ),
  );
}

/** Rung 14–15 handover truth — assumption of care recorded, note mandatory upstream. */
export function useRecordAssumptionOfCare() {
  return useEpisodeMutation<{ episodeId: string; note: string }>(({ episodeId, note }) =>
    apiClient.post<AlertEpisodeView>(
      `${BASE}/alert-episodes/${encodeURIComponent(episodeId)}/assumption-of-care`,
      { note },
    ),
  );
}

/**
 * §14.6 accountable closure — all four fields travel together; the upstream
 * refuses partial closures and that refusal surfaces with its real code.
 */
export function useResolveEpisode() {
  return useEpisodeMutation<{ episodeId: string } & AccountableClosure>(
    ({ episodeId, resolvedBy, assessment, action, outcome }) =>
      apiClient.post<AlertEpisodeView>(
        `${BASE}/alert-episodes/${encodeURIComponent(episodeId)}/resolve`,
        { resolvedBy, assessment, action, outcome },
      ),
  );
}

// ── Plans (OF-B22 read + review lanes) ──────────────────────────────────────

/** Clinician lane — plans for an explicit patient CPID. */
export function useMonitoringPlans(patientCpid: string | undefined) {
  return useQuery<MonitoringPlanView[]>({
    queryKey: telemonitoringKeys.plans(patientCpid ?? "unknown"),
    enabled: !!patientCpid,
    queryFn: () =>
      apiClient.get<MonitoringPlanView[]>(
        `${BASE}/plans?patientCpid=${encodeURIComponent(patientCpid!)}`,
      ),
  });
}

/** OF-B27 citizen self lane — the BFF resolves the patient from X-Actor-ID. */
export function useMyMonitoringPlans() {
  return useQuery<MonitoringPlanView[]>({
    queryKey: telemonitoringKeys.myPlans,
    queryFn: () => apiClient.get<MonitoringPlanView[]>(`${BASE}/my/plans`),
  });
}

export function useMonitoringPlan(planId: string | undefined) {
  return useQuery<MonitoringPlanView>({
    queryKey: telemonitoringKeys.plan(planId ?? "unknown"),
    enabled: !!planId,
    queryFn: () => apiClient.get<MonitoringPlanView>(`${BASE}/plans/${encodeURIComponent(planId!)}`),
  });
}

export function useThresholdProfiles(planId: string | undefined) {
  return useQuery<ThresholdProfileView[]>({
    queryKey: telemonitoringKeys.thresholds(planId ?? "unknown"),
    enabled: !!planId,
    queryFn: () =>
      apiClient.get<ThresholdProfileView[]>(
        `${BASE}/plans/${encodeURIComponent(planId!)}/threshold-profiles`,
      ),
  });
}

/** Record a completed clinical review — re-arms the plan's review-cadence timer. */
export function useRecordPlanReview() {
  const queryClient = useQueryClient();
  return useMutation<MonitoringPlanView, unknown, { planId: string }>({
    mutationFn: ({ planId }) =>
      apiClient.post<MonitoringPlanView>(`${BASE}/plans/${encodeURIComponent(planId)}/reviews`, {}),
    onSuccess: (_data, { planId }) => {
      void queryClient.invalidateQueries({ queryKey: telemonitoringKeys.plan(planId) });
    },
  });
}

// ── Readings (OF-B25 read surface — newest first, paged) ────────────────────

export function usePlanReadings(planId: string | undefined, page = 0, size = 50) {
  return useQuery<PagedReadingsView>({
    queryKey: telemonitoringKeys.readingsByPlan(planId ?? "unknown", page),
    enabled: !!planId,
    queryFn: () =>
      apiClient.get<PagedReadingsView>(
        `${BASE}/readings/by-plan/${encodeURIComponent(planId!)}?page=${page}&size=${size}`,
      ),
  });
}

// ── Device assignments (OF-B24) ─────────────────────────────────────────────

export function useDeviceAssignments(options: { planId?: string; patientCpid?: string }) {
  const { planId, patientCpid } = options;
  return useQuery<DeviceAssignmentView[]>({
    queryKey: telemonitoringKeys.assignments(planId, patientCpid),
    enabled: !!planId || !!patientCpid,
    queryFn: () => {
      const params = new URLSearchParams();
      if (planId) params.set("planId", planId);
      else if (patientCpid) params.set("patientCpid", patientCpid);
      return apiClient.get<DeviceAssignmentView[]>(`${BASE}/device-assignments?${params.toString()}`);
    },
  });
}

/** OF-B27 citizen self lane — my device assignments. */
export function useMyDeviceAssignments() {
  return useQuery<DeviceAssignmentView[]>({
    queryKey: telemonitoringKeys.myAssignments,
    queryFn: () => apiClient.get<DeviceAssignmentView[]>(`${BASE}/my/device-assignments`),
  });
}

/** Calibration-posture resolve — honest DEGRADED/UNVERIFIED, never masked OK. */
export function useResolvedDeviceAssignment(deviceRef: string | undefined) {
  return useQuery<ResolvedAssignmentView>({
    queryKey: telemonitoringKeys.resolvedDevice(deviceRef ?? "unknown"),
    enabled: !!deviceRef,
    queryFn: () =>
      apiClient.get<ResolvedAssignmentView>(
        `${BASE}/device-assignments/by-device/${encodeURIComponent(deviceRef!)}`,
      ),
  });
}

// ── Programme catalogue (§14.1) ─────────────────────────────────────────────

export function useMonitoringProgrammes() {
  return useQuery<MonitoringProgrammeView[]>({
    queryKey: telemonitoringKeys.programmes,
    queryFn: () => apiClient.get<MonitoringProgrammeView[]>(`${BASE}/programmes`),
  });
}

// ── OF-B23 — CHW monitoring tasks (PCT generic-task lane via existing BFF) ──

export interface PctTaskView {
  id?: string;
  taskType?: string;
  status?: string;
  assigneeId?: string;
  assigneeRole?: string;
  dueAt?: string | null;
  notes?: string | null;
  [key: string]: unknown;
}

/**
 * The CHW's monitoring tasks: the existing generic PCT task lane
 * (`/internal/v1/mobile/provider/tasks/mine` → PCT `/v1/tasks/my`, assignee
 * resolved from trust context) filtered to the MONITORING_* task types raised
 * by plan activation (MONITORING_ONBOARDING_VISIT) and the §14.7 rung-4
 * dispatch (MONITORING_ALERT_FOLLOWUP).
 */
export function useMyMonitoringTasks() {
  return useQuery<PctTaskView[]>({
    queryKey: ["tm-chw-monitoring-tasks"],
    queryFn: async () => {
      const res = await apiClient.get<{ data?: unknown }>(
        "/internal/v1/mobile/provider/tasks/mine?assigned_to=me",
      );
      const raw = res?.data;
      const list: PctTaskView[] = Array.isArray(raw)
        ? (raw as PctTaskView[])
        : Array.isArray((raw as { content?: unknown })?.content)
          ? ((raw as { content: PctTaskView[] }).content)
          : [];
      return list.filter((t) => (t.taskType ?? "").startsWith("MONITORING_"));
    },
  });
}
