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

// ── Alerts, command view, order sets, medications, observation stays, identity link ───────────
//
// Every hook below reaches a pct-service route that existed and had no caller until W15c. None of
// them use `?? []` on a failed read: a query that threw must surface as `isError` so the panel can
// say "could not be read" rather than rendering an empty list a clinician will read as "none".

export type EmergencyAlertRow = Record<string, unknown> & {
  alert_id?: string;
  episode_id?: string | null;
  alert_type?: string | null;
  severity?: string | null;
  status?: string | null;
  raised_at?: string | null;
  acknowledged_at?: string | null;
  acknowledged_by?: string | null;
  responded_at?: string | null;
  response_note?: string | null;
  closed_at?: string | null;
  detail?: string | null;
};

export type EmergencyCommandSummary = Record<string, unknown> & {
  episodes_by_state?: Record<string, number>;
  alerts_by_severity?: Record<string, number>;
};

/** Episode-by-state + alert-by-severity counts for one facility (W10). Proxied since W15c. */
export function useEmergencyCommandSummary(facilityId?: string) {
  return useQuery({
    queryKey: ["emergency-command-summary", facilityId],
    queryFn: async () =>
      unwrap<EmergencyCommandSummary>(await apiClient.get(`${BASE}/command-summary?facilityId=${facilityId}`)),
    enabled: !!facilityId,
    staleTime: 10_000,
    refetchInterval: 15_000,
  });
}

export interface EmergencyCommandBoard {
  items: {
    summary?: EmergencyCommandSummary;
    alerts?: EmergencyAlertRow[];
    episodes?: EmergencyEpisodeRow[];
  };
  failures: { source: string; error: string; message: string }[];
  meta?: { as_of?: string; degraded?: boolean };
}

/**
 * The whole board in one call. Unlike every other read here it does not 502 when a source is
 * unreachable — it returns whatever answered plus a `failures` list naming what did not, so one
 * blind upstream costs one tile rather than blanking a board someone is standing in front of.
 * The response is used as-is, never unwrapped through `unwrap`, because it has no `data` key.
 */
export function useEmergencyCommandBoard(facilityId?: string) {
  return useQuery({
    queryKey: ["emergency-command-board", facilityId],
    queryFn: async () =>
      (await apiClient.get(`${BASE}/command-board?facilityId=${facilityId}`)) as EmergencyCommandBoard,
    enabled: !!facilityId,
    staleTime: 10_000,
    refetchInterval: 15_000,
  });
}

export function useEmergencyAlertBoard(facilityId?: string) {
  return useQuery({
    queryKey: ["emergency-alert-board", facilityId],
    queryFn: async () =>
      unwrap<EmergencyAlertRow[]>(await apiClient.get(`${BASE}/alerts?facilityId=${facilityId}`)),
    enabled: !!facilityId,
    staleTime: 10_000,
    refetchInterval: 15_000,
  });
}

export function useEmergencyEpisodeAlerts(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-episode-alerts", episodeId],
    queryFn: async () =>
      unwrap<EmergencyAlertRow[]>(await apiClient.get(`${BASE}/${episodeId}/alerts`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

/**
 * The three distinct authorities on an alert. Acknowledging says a human saw it; responding says a
 * human acted; closing says the condition that raised it is gone — and only the close lifts pct's
 * partial unique index, so until W15c made it reachable a suppressed hazard could never re-raise.
 */
export function useEmergencyAlertActions(facilityId?: string, episodeId?: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["emergency-alert-board", facilityId] });
    void qc.invalidateQueries({ queryKey: ["emergency-command-summary", facilityId] });
    if (episodeId) void qc.invalidateQueries({ queryKey: ["emergency-episode-alerts", episodeId] });
  };

  return {
    acknowledge: useMutation({
      mutationFn: ({ alertId, ...body }: { alertId: string; acknowledgedBy: string }) =>
        apiClient.post(`${BASE}/alerts/${alertId}/acknowledge`, body),
      onSuccess: invalidate,
    }),
    respond: useMutation({
      mutationFn: ({ alertId, ...body }: { alertId: string; respondedBy: string; responseNote: string }) =>
        apiClient.post(`${BASE}/alerts/${alertId}/respond`, body),
      onSuccess: invalidate,
    }),
    close: useMutation({
      mutationFn: ({ alertId, ...body }: { alertId: string; closedBy: string; closureReason?: string }) =>
        apiClient.post(`${BASE}/alerts/${alertId}/close`, body),
      onSuccess: invalidate,
    }),
  };
}

export type EmergencyOrderSetRow = Record<string, unknown> & {
  instance_id?: string;
  order_set_code?: string | null;
  order_set_name?: string | null;
  status?: string | null;
  invoked_by?: string | null;
  invoked_at?: string | null;
  items?: EmergencyOrderSetItemRow[];
};

export type EmergencyOrderSetItemRow = Record<string, unknown> & {
  item_id?: string;
  order_code?: string | null;
  order_label?: string | null;
  order_type?: string | null;
  status?: string | null;
  ordered_at?: string | null;
  declined_at?: string | null;
  decline_reason?: string | null;
};

export function useEmergencyOrderSets(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-order-sets", episodeId],
    queryFn: async () =>
      unwrap<EmergencyOrderSetRow[]>(await apiClient.get(`${BASE}/${episodeId}/order-sets`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

export function useEmergencyOrderSetActions(episodeId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["emergency-order-sets", episodeId] });
  };

  return {
    invoke: useMutation({
      mutationFn: (body: { orderSetCode: string; invokedBy?: string }) =>
        apiClient.post(`${BASE}/${episodeId}/order-sets`, body),
      onSuccess: invalidate,
    }),
    orderItem: useMutation({
      mutationFn: ({ itemId, ...body }: { itemId: string; orderedBy: string }) =>
        apiClient.post(`${BASE}/order-sets/items/${itemId}/order`, body),
      onSuccess: invalidate,
    }),
    /** pct requires a reason; an empty one comes back as its own 4xx, which the panel must show. */
    declineItem: useMutation({
      mutationFn: ({ itemId, ...body }: { itemId: string; declinedBy: string; declineReason: string }) =>
        apiClient.post(`${BASE}/order-sets/items/${itemId}/decline`, body),
      onSuccess: invalidate,
    }),
  };
}

export type EmergencyMedicationRow = Record<string, unknown> & {
  administration_id?: string;
  medication_code?: string | null;
  medication_name?: string | null;
  dose_value?: number | null;
  dose_unit?: string | null;
  route?: string | null;
  administered_at?: string | null;
  administered_by?: string | null;
  witnessed_by?: string | null;
};

export function useEmergencyMedications(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-medications", episodeId],
    queryFn: async () =>
      unwrap<EmergencyMedicationRow[]>(await apiClient.get(`${BASE}/${episodeId}/medications`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

export function useRecordEmergencyMedication(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    // Dose value and unit are entered by the administering clinician and validated by pct's own
    // two-person authoriser-is-not-witness guard (V207). Nothing here computes a dose.
    mutationFn: (body: {
      medicationCode: string;
      medicationName?: string;
      doseValue: number;
      doseUnit: string;
      route: string;
      administeredBy: string;
      witnessedBy?: string;
    }) => apiClient.post(`${BASE}/${episodeId}/medications`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["emergency-medications", episodeId] });
    },
  });
}

export type EmergencyObservationStayRow = Record<string, unknown> & {
  stay_id?: string;
  episode_id?: string | null;
  status?: string | null;
  reason?: string | null;
  started_at?: string | null;
  expected_end_at?: string | null;
  ended_at?: string | null;
  outcome?: string | null;
};

export function useEmergencyObservationStays(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-observation-stays", episodeId],
    queryFn: async () =>
      unwrap<EmergencyObservationStayRow[]>(await apiClient.get(`${BASE}/${episodeId}/observation-stays`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

/** Neither an admission nor a discharge — the genuinely unowned middle state pct models directly. */
export function useEmergencyObservationStayActions(episodeId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["emergency-observation-stays", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
  };

  return {
    start: useMutation({
      mutationFn: (body: { reason: string; startedBy?: string; expectedEndAt?: string }) =>
        apiClient.post(`${BASE}/${episodeId}/observation-stays`, body),
      onSuccess: invalidate,
    }),
    end: useMutation({
      mutationFn: ({ stayId, ...body }: { stayId: string; outcome: string; endedBy?: string }) =>
        apiClient.post(`${BASE}/observation-stays/${stayId}/end`, body),
      onSuccess: invalidate,
    }),
  };
}

export type EmergencyIdentityLinkRow = Record<string, unknown> & {
  link_id?: string;
  resolved_subject_cpid?: string | null;
  resolution_method?: string | null;
  evidence_ref?: string | null;
  vito_merge_id?: string | null;
  resolved_by?: string | null;
  resolved_at?: string | null;
  superseded_by?: string | null;
};

export function useEmergencyIdentityLinks(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-identity-links", episodeId],
    queryFn: async () =>
      unwrap<EmergencyIdentityLinkRow[]>(await apiClient.get(`${BASE}/${episodeId}/identity-link`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

/** Append-only: resolving an identity links, it never overwrites. Both identities are retained. */
export function useLinkEmergencyIdentity(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      resolvedSubjectCpid: string;
      resolutionMethod: string;
      evidenceRef?: string;
      vitoMergeId?: string;
      resolvedBy?: string;
    }) => apiClient.post(`${BASE}/${episodeId}/identity-link`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["emergency-identity-links", episodeId] });
      void qc.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
    },
  });
}

export type EmergencyDispositionRow = Record<string, unknown> & {
  disposition_id?: string;
  disposition_type?: string | null;
  destination?: string | null;
  recorded_at?: string | null;
  recorded_by?: string | null;
  last_seen_at?: string | null;
  notes?: string | null;
};

export function useEmergencyDisposition(episodeId?: string) {
  return useQuery({
    queryKey: ["emergency-disposition", episodeId],
    queryFn: async () =>
      unwrap<EmergencyDispositionRow>(await apiClient.get(`${BASE}/${episodeId}/disposition`)),
    enabled: !!episodeId,
    staleTime: 5_000,
  });
}

export function useRecordEmergencyDisposition(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`${BASE}/${episodeId}/disposition`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["emergency-disposition", episodeId] });
      void qc.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
    },
  });
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
