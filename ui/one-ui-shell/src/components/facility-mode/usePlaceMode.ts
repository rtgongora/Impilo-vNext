import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface BffEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface PlaceModeSummary {
  openAlerts: number;
  activeOutbreaks: number;
  teams: number;
}

export interface SurveillanceAlert {
  alertId: string;
  siteId: string | null;
  signalType: string;
  conditionCode: string | null;
  severity: string;
  status: string;
  caseCount: number;
  description: string | null;
  detectedAt: string | null;
}

export interface Outbreak {
  outbreakId: string;
  conditionCode: string;
  name: string;
  severity: string;
  status: string;
  areaDescription: string | null;
  caseCount: number;
  deathCount: number;
  declaredAt: string | null;
}

export interface FieldTeam {
  teamId: string;
  name: string;
  teamType: string;
  status: string;
  leadHealthId: string | null;
  memberCount: number;
}

const BASE = "/internal/v1/place-mode";

export function usePlaceModeSummary() {
  return useQuery({
    queryKey: ["place-mode-summary"],
    queryFn: async () => (await apiClient.get<BffEnvelope<PlaceModeSummary>>(`${BASE}/summary`)).data,
  });
}

export function useSurveillanceAlerts(status?: string) {
  return useQuery({
    queryKey: ["place-mode-alerts", status ?? null],
    queryFn: async () =>
      (await apiClient.get<BffEnvelope<SurveillanceAlert[]>>(
        `${BASE}/alerts${status ? `?status=${encodeURIComponent(status)}` : ""}`,
      )).data,
  });
}

export function useOutbreaks(status?: string) {
  return useQuery({
    queryKey: ["place-mode-outbreaks", status ?? null],
    queryFn: async () =>
      (await apiClient.get<BffEnvelope<Outbreak[]>>(
        `${BASE}/outbreaks${status ? `?status=${encodeURIComponent(status)}` : ""}`,
      )).data,
  });
}

export function useFieldTeams() {
  return useQuery({
    queryKey: ["place-mode-teams"],
    queryFn: async () => (await apiClient.get<BffEnvelope<FieldTeam[]>>(`${BASE}/field-teams`)).data,
  });
}

export function useCreateOutbreak() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      conditionCode: string;
      name: string;
      severity?: string;
      areaDescription?: string;
    }) => (await apiClient.post<BffEnvelope<Outbreak>>(`${BASE}/outbreaks`, input)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["place-mode-outbreaks"] });
      qc.invalidateQueries({ queryKey: ["place-mode-summary"] });
    },
  });
}

export function useDeployTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      teamId: string;
      outbreakId?: string;
      siteId?: string;
      objective?: string;
    }) =>
      (await apiClient.post<BffEnvelope<unknown>>(`${BASE}/field-teams/${input.teamId}/deploy`, {
        outbreakId: input.outbreakId,
        siteId: input.siteId,
        objective: input.objective,
      })).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["place-mode-teams"] });
    },
  });
}
