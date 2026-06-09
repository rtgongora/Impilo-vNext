import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface ProcedureEpisode {
  id: string;
  patient_id: string;
  procedure_name: string;
  procedure_code?: string;
  status: string;
  scheduled_at?: string;
  surgeon_id?: string;
  anaesthetist_id?: string;
  preop_assessments?: Array<Record<string, unknown>>;
  checklist?: Array<{
    id: string;
    phase: string;
    item_code: string;
    item_label: string;
    completed: boolean;
  }>;
  intraop_events?: Array<Record<string, unknown>>;
  postop?: Record<string, unknown>;
  consent_status?: string;
  mvumo_consent_request_id?: string;
  consent_proof_ref?: string;
  documents?: Array<Record<string, unknown>>;
  consumables?: Array<Record<string, unknown>>;
  booking_id?: string;
  anaesthesia_scores?: Array<{
    id: string;
    score_type: string;
    phase: string;
    total_score?: number | null;
    interpretation?: string;
    risk_band?: string;
    recorded_at?: string;
  }>;
  anaesthesia_score_suggestions?: Array<{
    score_type: string;
    reason: string;
    recommended: boolean;
    priority: number;
  }>;
}

export function useProcedureEpisodes(patientId: string) {
  return useQuery({
    queryKey: ["procedure-episodes", patientId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: ProcedureEpisode[] }>(
        `/internal/v1/procedures/episodes?patientId=${encodeURIComponent(patientId)}`
      );
      return res.data ?? [];
    },
    enabled: !!patientId,
  });
}

export function useProcedureEpisode(episodeId: string) {
  return useQuery({
    queryKey: ["procedure-episode", episodeId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: ProcedureEpisode }>(
        `/internal/v1/procedures/episodes/${episodeId}`
      );
      return res.data;
    },
    enabled: !!episodeId,
  });
}

export function useCreateProcedureEpisode(patientId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      const res = await apiClient.post<{ data: ProcedureEpisode }>("/internal/v1/procedures/episodes", {
        patientId,
        placePreopOrders: true,
        ...body,
      });
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["procedure-episodes", patientId] });
      void qc.invalidateQueries({ queryKey: ["ehr", "patient-procedures", patientId] });
    },
  });
}

export function useProcedureEpisodeAction(episodeId: string) {
  const qc = useQueryClient();
  const invalidate = () => void qc.invalidateQueries({ queryKey: ["procedure-episode", episodeId] });

  return {
    submitPreop: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/preop`, body),
      onSuccess: invalidate,
    }),
    completeChecklist: useMutation({
      mutationFn: ({ phase, itemIds }: { phase: string; itemIds: string[] }) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/checklist/${phase}`, {
          itemIds,
          completedBy: "provider-web",
        }),
      onSuccess: invalidate,
    }),
    requestConsent: useMutation({
      mutationFn: () =>
        apiClient.post<{ data: ProcedureEpisode }>(`/internal/v1/procedures/episodes/${episodeId}/consent`, {}),
      onSuccess: invalidate,
    }),
    syncConsent: useMutation({
      mutationFn: () =>
        apiClient.post<{ data: ProcedureEpisode }>(`/internal/v1/procedures/episodes/${episodeId}/consent/sync`, {}),
      onSuccess: invalidate,
    }),
    startProcedure: useMutation({
      mutationFn: () =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/start`, {}),
      onSuccess: invalidate,
    }),
    uploadOperativeDocument: useMutation({
      mutationFn: async (file: File) => {
        const form = new FormData();
        form.append("file", file);
        return apiClient.postForm<{ data: Record<string, unknown> }>(
          `/internal/v1/procedures/episodes/${episodeId}/documents/upload`,
          form
        );
      },
      onSuccess: invalidate,
    }),
    recordConsumable: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/consumables`, body),
      onSuccess: invalidate,
    }),
    recordIntraopVitals: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/intraop/vitals`, body),
      onSuccess: invalidate,
    }),
    recordAnaesthesiaScore: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/anaesthesia/scores`, body),
      onSuccess: invalidate,
    }),
    recordIntraop: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/intraop/events`, body),
      onSuccess: invalidate,
    }),
    enterPacu: useMutation({
      mutationFn: () =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/pacu`, { recordedBy: "provider-web" }),
      onSuccess: invalidate,
    }),
    completePostop: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/postop`, body),
      onSuccess: invalidate,
    }),
    completeEpisode: useMutation({
      mutationFn: () =>
        apiClient.post(`/internal/v1/procedures/episodes/${episodeId}/complete`, {}),
      onSuccess: invalidate,
    }),
  };
}
