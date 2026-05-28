"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface NotificationTemplateRow {
  id?: string;
  key?: string;
  name?: string;
  status?: string;
  channel?: string;
  [key: string]: unknown;
}

function asRows(raw: unknown): NotificationTemplateRow[] {
  if (Array.isArray(raw)) return raw as NotificationTemplateRow[];
  if (raw && typeof raw === "object") {
    const r = raw as Record<string, unknown>;
    if (Array.isArray(r.content)) return r.content as NotificationTemplateRow[];
    if (Array.isArray(r.items)) return r.items as NotificationTemplateRow[];
  }
  return [];
}

export function useNotificationTemplates() {
  return useQuery({
    queryKey: ["notification-templates"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/notifications/templates");
      return asRows(res.data);
    },
  });
}

export function useCreateNotificationTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/notifications/templates", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["notification-templates"] }),
  });
}

export function usePublishNotificationTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/notifications/templates/${encodeURIComponent(templateId)}/publish`,
        {},
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["notification-templates"] }),
  });
}

export function useRetireNotificationTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/notifications/templates/${encodeURIComponent(templateId)}/retire`,
        {},
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["notification-templates"] }),
  });
}
