/**
 * Health Intelligence Plane — governed form schemas (forms-service via BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type FormsEnvelope = ApiResponse<unknown>;

const FORMS_LIST_KEY = ["extensions", "forms"] as const;

export function useFormSchemas() {
  return useQuery({
    queryKey: FORMS_LIST_KEY,
    queryFn: () => apiClient.get<FormsEnvelope>("/internal/v1/extensions/forms"),
  });
}

export function useFormSchema(formId: string | undefined) {
  return useQuery({
    queryKey: [...FORMS_LIST_KEY, formId],
    queryFn: () => apiClient.get<FormsEnvelope>(`/internal/v1/extensions/forms/${encodeURIComponent(formId!)}`),
    enabled: Boolean(formId),
  });
}

export function useCreateFormSchema() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<FormsEnvelope>("/internal/v1/extensions/forms", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FORMS_LIST_KEY });
    },
  });
}

export function usePublishFormSchema() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (formId: string) =>
      apiClient.post<FormsEnvelope>(`/internal/v1/extensions/forms/${encodeURIComponent(formId)}/publish`, {}),
    onSuccess: (_data, formId) => {
      void queryClient.invalidateQueries({ queryKey: FORMS_LIST_KEY });
      void queryClient.invalidateQueries({ queryKey: [...FORMS_LIST_KEY, formId] });
    },
  });
}

export function useCreateFormVersion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ formId, body }: { formId: string; body: Record<string, unknown> }) =>
      apiClient.post<FormsEnvelope>(
        `/internal/v1/extensions/forms/${encodeURIComponent(formId)}/versions`,
        body
      ),
    onSuccess: (_data, { formId }) => {
      void queryClient.invalidateQueries({ queryKey: FORMS_LIST_KEY });
      void queryClient.invalidateQueries({ queryKey: [...FORMS_LIST_KEY, formId] });
    },
  });
}
