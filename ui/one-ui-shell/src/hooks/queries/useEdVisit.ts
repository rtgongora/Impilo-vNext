"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type EdVisit = Record<string, unknown>;

const BASE = "/internal/v1/ed";

export function useEdVisits(facilityId?: string, status?: string) {
  const params = new URLSearchParams();
  if (facilityId) params.set("facilityId", facilityId);
  if (status) params.set("status", status);
  const qs = params.toString();
  return useQuery({
    queryKey: ["ed-visits", facilityId, status],
    queryFn: async () => {
      const res = await apiClient.get<{ data: EdVisit[] }>(`${BASE}/visits${qs ? `?${qs}` : ""}`);
      return res.data ?? [];
    },
  });
}

export function useEdVisit(visitId: string) {
  return useQuery({
    queryKey: ["ed-visit", visitId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: EdVisit }>(`${BASE}/visits/${visitId}`);
      return res.data;
    },
    enabled: !!visitId,
  });
}

export function useEdVisitActions(visitId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["ed-visit", visitId] });
    void qc.invalidateQueries({ queryKey: ["ed-visits"] });
  };

  return {
    scoreTriage: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post<{ data: Record<string, unknown> }>(`${BASE}/triage/score`, body),
    }),
    triage: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/triage`, body),
      onSuccess: invalidate,
    }),
    assignZone: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/zone`, body),
      onSuccess: invalidate,
    }),
    startEncounter: useMutation({
      mutationFn: () => apiClient.post(`${BASE}/visits/${visitId}/encounter`, {}),
      onSuccess: invalidate,
    }),
    bindProtocol: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/protocol`, body),
      onSuccess: invalidate,
    }),
    activateTrauma: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/trauma/activate`, body),
      onSuccess: invalidate,
    }),
    recordTraumaSurvey: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/trauma/survey`, body),
      onSuccess: invalidate,
    }),
    endTrauma: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/trauma/end`, body),
      onSuccess: invalidate,
    }),
    pageTeam: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/page`, body),
      onSuccess: invalidate,
    }),
    disposition: useMutation({
      mutationFn: (body: Record<string, unknown>) =>
        apiClient.post(`${BASE}/visits/${visitId}/disposition`, body),
      onSuccess: invalidate,
    }),
    advancePathway: useMutation({
      mutationFn: ({ sessionId, answers }: { sessionId: string; answers: Record<string, unknown> }) =>
        apiClient.post(`${BASE}/pathways/sessions/${sessionId}/advance`, { answers }),
      onSuccess: invalidate,
    }),
  };
}

export function useOpenEdVisit() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<{ data: EdVisit }>(`${BASE}/visits`, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["ed-visits"] }),
  });
}
