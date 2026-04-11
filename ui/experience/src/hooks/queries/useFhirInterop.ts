/**
 * FHIR Interoperability Hooks — Health OS §10 (Interoperability)
 *
 * Queries the FHIR gateway via the Experience BFF FhirInteropController.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useFhirCapabilityStatement() {
  return useQuery({
    queryKey: ["fhir", "metadata"],
    queryFn: () => apiClient.get<ApiResponse<Record<string, unknown>>>("/internal/v1/fhir/metadata"),
  });
}

export function useFhirSearch(resourceType: string, params: string, enabled = true) {
  return useQuery({
    queryKey: ["fhir", "search", resourceType, params],
    queryFn: () => apiClient.get<ApiResponse<Record<string, unknown>>>(`/internal/v1/fhir/${resourceType}?params=${encodeURIComponent(params)}`),
    enabled,
  });
}

export function useFhirResource(resourceType: string, id: string | undefined) {
  return useQuery({
    queryKey: ["fhir", "resource", resourceType, id],
    queryFn: () => apiClient.get<ApiResponse<Record<string, unknown>>>(`/internal/v1/fhir/${resourceType}/${id}`),
    enabled: !!id,
  });
}
