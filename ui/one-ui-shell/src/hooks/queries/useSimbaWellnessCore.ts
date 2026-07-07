/**
 * Experience UI — SIMBA wellness core completion hooks (Wave 2/3): timeline, preventive reminders,
 * plan recommendations, follow-ups, consent. Served by Simba (8125) via the experience-bff proxy
 * of /internal/v1/wellness/**.
 */

import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Wellness timeline (keyset-paginated) ────────────────────────────────────
export interface TimelineEvent {
  eventId: string;
  id: number;
  eventCategory: string;
  title: string;
  summary?: string | null;
  severity: string;
  occurredAt: string;
  refType?: string | null;
  refId?: string | null;
}

interface TimelinePageResponse {
  data: TimelineEvent[];
  meta: { next_cursor: number | null };
}

export function useWellnessTimeline(cpid?: string | null, limit = 20) {
  return useInfiniteQuery<TimelinePageResponse>({
    queryKey: ["wellness-timeline", cpid ?? null],
    enabled: !!cpid,
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const sp = new URLSearchParams();
      sp.set("person_cpid", cpid ?? "");
      sp.set("limit", String(limit));
      if (pageParam != null) sp.set("cursor", String(pageParam));
      return apiClient.get<TimelinePageResponse>(
        `/internal/v1/wellness/timeline?${sp.toString()}`,
      );
    },
    getNextPageParam: (last) => last.meta?.next_cursor ?? undefined,
  });
}

// ── Preventive reminders ────────────────────────────────────────────────────
export function useReminders(cpid?: string | null) {
  return useQuery<ApiResponse<unknown[]>>({
    queryKey: ["wellness-reminders", cpid ?? null],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/wellness/reminders?person_cpid=${encodeURIComponent(cpid ?? "")}`,
      ),
    enabled: !!cpid,
  });
}

export function useSnoozeReminder() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { reminderId: string; until: string }>({
    mutationFn: ({ reminderId, until }) =>
      apiClient.post(`/internal/v1/wellness/reminders/${reminderId}/snooze`, { until }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["wellness-reminders"] }),
  });
}

export function useCompleteReminder() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { reminderId: string }>({
    mutationFn: ({ reminderId }) =>
      apiClient.post(`/internal/v1/wellness/reminders/${reminderId}/complete`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["wellness-reminders"] }),
  });
}

// ── Plan recommendations ────────────────────────────────────────────────────
export function useRecommendedPlans(cpid?: string | null) {
  return useQuery<ApiResponse<unknown[]>>({
    queryKey: ["wellness-recommendations", cpid ?? null],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/wellness/programs/recommendations?person_cpid=${encodeURIComponent(cpid ?? "")}`,
      ),
    enabled: !!cpid,
  });
}

export function useAssembleRecommendedPlan() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<unknown>,
    Error,
    { plan_id: string; person_cpid: string }
  >({
    mutationFn: (body) =>
      apiClient.post("/internal/v1/wellness/programs/recommendations", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["wellness-enrollments"] });
      void qc.invalidateQueries({ queryKey: ["wellness-reminders"] });
      void qc.invalidateQueries({ queryKey: ["wellness-timeline"] });
    },
  });
}

// ── Consent preferences ─────────────────────────────────────────────────────
export interface ConsentPreference {
  caregiverInvolvement: boolean;
  providerInvolvement: boolean;
  programmeInvolvement: boolean;
  sensitiveCategories: string;
  dataSharingScope: string;
}

export function useConsentPreferences(cpid?: string | null) {
  return useQuery<ApiResponse<ConsentPreference>>({
    queryKey: ["wellness-consent", cpid ?? null],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/wellness/consent-preferences?person_cpid=${encodeURIComponent(cpid ?? "")}`,
      ),
    enabled: !!cpid,
  });
}

export function useUpdateConsentPreferences() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<ConsentPreference>,
    Error,
    {
      person_cpid: string;
      caregiver_involvement?: boolean;
      provider_involvement?: boolean;
      programme_involvement?: boolean;
      sensitive_categories?: string[];
      data_sharing_scope?: string;
    }
  >({
    mutationFn: (body) =>
      apiClient.put("/internal/v1/wellness/consent-preferences", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["wellness-consent"] }),
  });
}

// ── Follow-ups ──────────────────────────────────────────────────────────────
export function useFollowUps(cpid?: string | null) {
  return useQuery<ApiResponse<unknown[]>>({
    queryKey: ["wellness-follow-ups", cpid ?? null],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/wellness/follow-ups?person_cpid=${encodeURIComponent(cpid ?? "")}`,
      ),
    enabled: !!cpid,
  });
}

// ── Provider workbench (BFF composition) ────────────────────────────────────
export function useWorkbenchClientSummary(cpid?: string | null) {
  return useQuery<ApiResponse<Record<string, unknown>>>({
    queryKey: ["wellness-workbench-summary", cpid ?? null],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/wellness/workbench/client/${encodeURIComponent(cpid ?? "")}/summary`,
      ),
    enabled: !!cpid,
  });
}

export function useCreateWorkbenchFollowUp() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<unknown>,
    Error,
    { cpid: string; title: string; detail?: string; action_type?: string; severity?: string }
  >({
    mutationFn: ({ cpid, ...body }) =>
      apiClient.post(`/internal/v1/wellness/workbench/client/${cpid}/follow-up`, body),
    onSuccess: (_r, vars) => {
      void qc.invalidateQueries({ queryKey: ["wellness-workbench-summary", vars.cpid] });
    },
  });
}

export function useWellnessAggregate() {
  return useQuery<ApiResponse<Record<string, number>>>({
    queryKey: ["wellness-aggregate-dashboard"],
    queryFn: () => apiClient.get("/internal/v1/wellness/aggregate/dashboard"),
  });
}

/** Flatten paginated timeline pages into a single list. */
export function flattenTimeline(
  pages?: { data: TimelineEvent[] }[],
): TimelineEvent[] {
  if (!pages) return [];
  return pages.flatMap((p) => p.data ?? []);
}
