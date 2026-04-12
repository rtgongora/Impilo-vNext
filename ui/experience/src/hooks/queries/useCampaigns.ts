/**
 * Public health campaigns — TanStack Query hooks (campaigns-service via BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { extractPublicHealthList, normalizeCampaign, type PublicHealthCampaign } from "./usePublicHealth";

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function listCampaignRows(data: unknown): unknown[] {
  const d = asRecord(data);
  if (Array.isArray(d.content)) return d.content as unknown[];
  return extractPublicHealthList(data, ["items"]);
}

export function useCampaigns(status?: string) {
  return useQuery({
    queryKey: ["campaigns", "list", status ?? ""],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/campaigns");
      let rows: PublicHealthCampaign[] = listCampaignRows(response.data).map(normalizeCampaign);
      if (status) {
        const st = status.toUpperCase();
        rows = rows.filter((c) => c.status.toUpperCase() === st);
      }
      return rows;
    },
  });
}

export function useCampaign(id: string | undefined) {
  return useQuery({
    queryKey: ["campaigns", "detail", id ?? ""],
    queryFn: async () => {
      const raw = await apiClient.get<{ data: unknown }>(
        `/internal/v1/public-health/campaigns/${encodeURIComponent(id!)}`,
      );
      return normalizeCampaign(raw.data);
    },
    enabled: Boolean(id),
  });
}

export type CreateCampaignBody = {
  name: string;
  description?: string;
  campaignType: string;
  targetGroup: string;
  messageTemplate?: string;
  channel?: string;
};

export function useCreateCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateCampaignBody) =>
      apiClient.post<unknown>("/internal/v1/public-health/campaigns", {
        name: body.name,
        description: body.description ?? "",
        campaignType: body.campaignType,
        targetGroup: body.targetGroup,
        messageTemplate: body.messageTemplate ?? "{}",
        channel: body.channel ?? "SMS",
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["campaigns"] });
      void qc.invalidateQueries({ queryKey: ["public-health-campaigns"] });
    },
  });
}

export function useLaunchCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (campaignId: string) =>
      apiClient.post<unknown>(`/internal/v1/public-health/campaigns/${encodeURIComponent(campaignId)}/dispatch`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["campaigns"] });
      void qc.invalidateQueries({ queryKey: ["public-health-campaigns"] });
    },
  });
}

export function useCloseCampaign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (campaignId: string) =>
      apiClient.post<unknown>(`/internal/v1/public-health/campaigns/${encodeURIComponent(campaignId)}/close`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["campaigns"] });
      void qc.invalidateQueries({ queryKey: ["public-health-campaigns"] });
    },
  });
}

export type { PublicHealthCampaign };
