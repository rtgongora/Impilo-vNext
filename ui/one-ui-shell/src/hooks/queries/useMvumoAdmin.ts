"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type MvumoTemplate = Record<string, unknown>;
export type MvumoConsentRequestPayload = {
  subjectPatientRef: string;
  templateKey: string;
  workflowRef?: string;
  consentType?: string;
  channel?: string;
};

export function useMvumoAdminTemplates() {
  return useQuery<ApiResponse<MvumoTemplate[] | MvumoTemplate>>({
    queryKey: ["mvumo-admin", "templates"],
    queryFn: () => apiClient.get<ApiResponse<MvumoTemplate[] | MvumoTemplate>>("/internal/v1/mvumo-admin/templates"),
  });
}

export function useCreateMvumoTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<MvumoTemplate>>("/internal/v1/mvumo-admin/templates", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mvumo-admin", "templates"] }),
  });
}

export function useUpdateMvumoTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ templateId, body }: { templateId: string; body: Record<string, unknown> }) =>
      apiClient.put<ApiResponse<MvumoTemplate>>(
        `/internal/v1/mvumo-admin/templates/${encodeURIComponent(templateId)}`,
        body,
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mvumo-admin", "templates"] }),
  });
}

export function useCreateMvumoConsentRequest() {
  return useMutation({
    mutationFn: (body: MvumoConsentRequestPayload) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/mvumo-admin/consent-requests", body),
  });
}
