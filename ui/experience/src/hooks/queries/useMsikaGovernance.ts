import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type MsikaGovernanceJson = unknown;

function buildQuery(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value != null && value !== "") query.set(key, String(value));
  }
  const suffix = query.toString();
  return suffix ? `?${suffix}` : "";
}

function invalidateGovernance(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["msika", "governance"] });
}

export function useMsikaCatalogs(params: { page?: number; size?: number }, enabled = true) {
  return useQuery<MsikaGovernanceJson>({
    queryKey: ["msika", "governance", "catalogs", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<MsikaGovernanceJson>(`/internal/v1/msika/catalogs${buildQuery(params)}`),
    enabled,
  });
}

export function useCreateMsikaCatalog() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, { name: string; scope: string; version: string }>({
    mutationFn: (body) => apiClient.post<MsikaGovernanceJson>("/internal/v1/msika/catalogs", body),
    onSuccess: () => invalidateGovernance(qc),
  });
}

export function useSubmitMsikaCatalogReview() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, string>({
    mutationFn: (catalogId) =>
      apiClient.post<MsikaGovernanceJson>(
        `/internal/v1/msika/catalogs/${encodeURIComponent(catalogId)}/submit-review`,
      ),
    onSuccess: () => invalidateGovernance(qc),
  });
}

export function useApproveMsikaCatalog() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, string>({
    mutationFn: (catalogId) =>
      apiClient.post<MsikaGovernanceJson>(
        `/internal/v1/msika/catalogs/${encodeURIComponent(catalogId)}/approve`,
      ),
    onSuccess: () => invalidateGovernance(qc),
  });
}

export function usePublishMsikaCatalog() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, string>({
    mutationFn: (catalogId) =>
      apiClient.post<MsikaGovernanceJson>(
        `/internal/v1/msika/catalogs/${encodeURIComponent(catalogId)}/publish`,
      ),
    onSuccess: () => invalidateGovernance(qc),
  });
}

export function useMsikaPendingMappings(params: { page?: number; size?: number }, enabled = true) {
  return useQuery<MsikaGovernanceJson>({
    queryKey: ["msika", "governance", "mappings", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<MsikaGovernanceJson>(
        `/internal/v1/msika/mappings/pending${buildQuery(params)}`,
      ),
    enabled,
  });
}

export function useApproveMsikaMapping() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, string>({
    mutationFn: (mappingId) =>
      apiClient.post<MsikaGovernanceJson>(
        `/internal/v1/msika/mappings/${encodeURIComponent(mappingId)}/approve`,
      ),
    onSuccess: () => invalidateGovernance(qc),
  });
}

export function useRejectMsikaMapping() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, string>({
    mutationFn: (mappingId) =>
      apiClient.post<MsikaGovernanceJson>(
        `/internal/v1/msika/mappings/${encodeURIComponent(mappingId)}/reject`,
      ),
    onSuccess: () => invalidateGovernance(qc),
  });
}

export function useImportMsikaCatalogCsv() {
  const qc = useQueryClient();
  return useMutation<MsikaGovernanceJson, unknown, { catalogId: string; file: File }>({
    mutationFn: ({ catalogId, file }) => {
      const body = new FormData();
      body.append("catalogId", catalogId);
      body.append("file", file);
      return apiClient.postForm<MsikaGovernanceJson>("/internal/v1/msika/import", body);
    },
    onSuccess: () => invalidateGovernance(qc),
  });
}
