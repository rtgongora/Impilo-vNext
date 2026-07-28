/**
 * The pct-service emergency episode spine + acceptance handshake — Experience BFF
 * (/internal/v1/emergency-episodes/**, W19b). Distinct from useEmergency.ts's inpatient-backed
 * activation/resuscitation surface and from useDaidzai.ts's cross-facility trauma episode: this is
 * pct.emergency_episode, the continuum-side canonical episode (R2) — the FSM, the 14 entry routes,
 * and THE ACCEPTANCE HANDSHAKE (responsibility transfers only on the accepting party's own write,
 * never a request or a timeout).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** Raw row shape from pct's EmergencyEpisodeController (snake_case keys). */
export type EmergencyEpisodeRow = Record<string, unknown> & {
  episode_id?: string;
  episode_reference?: string | null;
  state?: string | null;
  entry_route?: string | null;
  episode_class?: string | null;
  journey_id?: string | null;
  subject_cpid?: string | null;
  identity_mode?: string | null;
  sensitive?: boolean;
  arrived_at?: string | null;
  anchor_resolved_at?: string | null;
  current_location?: string | null;
  location_confirmed_at?: string | null;
  handover_id?: string | null;
  outcome?: string | null;
  closed_at?: string | null;
};

export type EmergencyHandoverRow = Record<string, unknown> & {
  handover_id?: string;
  episode_id?: string;
  target_type?: string | null;
  target_service?: string | null;
  status?: string | null;
  requested_by?: string | null;
  requested_at?: string | null;
  response_due_at?: string | null;
  accepted_by?: string | null;
  accepting_ref?: string | null;
  declined_by?: string | null;
  decline_reason?: string | null;
  expired_at?: string | null;
  rito_case_ref?: string | null;
};

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

const BASE = "/internal/v1/emergency-episodes";

/** The facility board: every open episode. */
export function useEmergencyEpisodeBoard(facilityId?: string) {
  return useQuery({
    queryKey: ["emergency-episode-board", facilityId],
    queryFn: async () =>
      unwrap<EmergencyEpisodeRow[]>(await apiClient.get(`${BASE}?facilityId=${facilityId}`)) ?? [],
    enabled: !!facilityId,
    staleTime: 10_000,
    refetchInterval: 10_000,
  });
}

export function useEmergencyEpisode(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-episode", episodeId],
    queryFn: async () => unwrap<EmergencyEpisodeRow>(await apiClient.get(`${BASE}/${episodeId}`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

export function useEmergencyHandoverHistory(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-episode-handovers", episodeId],
    queryFn: async () =>
      unwrap<EmergencyHandoverRow[]>(await apiClient.get(`${BASE}/${episodeId}/handovers`)) ?? [],
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

export function useOpenEmergencyEpisode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      facilityId: string;
      entryRoute: string;
      subjectCpid?: string;
      journeyId?: string;
      episodeClass?: string;
      sensitive?: boolean;
    }) => apiClient.post<{ data: EmergencyEpisodeRow }>(BASE, body),
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({ queryKey: ["emergency-episode-board", vars.facilityId] });
    },
  });
}

/** Every action on one episode: FSM transitions, location, and the acceptance handshake. */
export function useEmergencyEpisodeActions(episodeId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-episode-handovers", episodeId] });
  };

  return {
    arrive: useMutation({
      mutationFn: (body: { journeyId: string; encounterId?: number }) =>
        apiClient.post(`${BASE}/${episodeId}/arrive`, body),
      onSuccess: invalidate,
    }),
    transition: useMutation({
      mutationFn: (body: { state: string; outcome?: string }) =>
        apiClient.post(`${BASE}/${episodeId}/transition`, body),
      onSuccess: invalidate,
    }),
    confirmLocation: useMutation({
      mutationFn: (body: { location: string }) => apiClient.post(`${BASE}/${episodeId}/location`, body),
      onSuccess: invalidate,
    }),
    /** Request a handover. Requesting is NOT accepting — the episode moves to OPEN_AWAITING_ACCEPTANCE. */
    requestHandover: useMutation({
      mutationFn: (body: { targetType: string; targetService?: string; requestedBy: string; reason?: string }) =>
        apiClient.post<{ data: EmergencyHandoverRow }>(`${BASE}/${episodeId}/handover`, body),
      onSuccess: invalidate,
    }),
  };
}

/** THE ACCEPTANCE HANDSHAKE — these are the only writes that resolve a pending handover. */
export function useEmergencyHandoverActions(handoverId: string, episodeId?: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    if (episodeId) {
      void qc.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
      void qc.invalidateQueries({ queryKey: ["emergency-episode-handovers", episodeId] });
    }
  };

  return {
    /** Carries the accepting party's OWN record id — the handshake itself. */
    accept: useMutation({
      mutationFn: (body: { acceptedBy: string; acceptingRef: string; acceptingService?: string }) =>
        apiClient.post(`${BASE}/handovers/${handoverId}/accept`, body),
      onSuccess: invalidate,
    }),
    decline: useMutation({
      mutationFn: (body: { declinedBy: string; reason: string }) =>
        apiClient.post(`${BASE}/handovers/${handoverId}/decline`, body),
      onSuccess: invalidate,
    }),
    expire: useMutation({
      mutationFn: (body: { expiredBy: string; ritoCaseRef?: string }) =>
        apiClient.post(`${BASE}/handovers/${handoverId}/expire`, body),
      onSuccess: invalidate,
    }),
  };
}
