import { useQuery, useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface MaskedClient {
  healthId: string;
  maskedName: string;
  maskedDob: string;
  maskedSex: string;
  matchScore?: number;
}

export interface InternalClientFull {
  healthId: string;
  impiloId: string;
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  sex: string;
  address?: string;
  phoneNumber?: string;
  status: string;
  assuranceLevel: string;
}

/**
 * Perform a privacy-preserving internal search for client records.
 * Returns masked results with match scores.
 */
export function useInternalClientSearch() {
  return useMutation({
    mutationFn: (body: { query: string; page?: number; size?: number }) =>
      apiClient.post<ApiResponse<PagedItems<MaskedClient>>>("/internal/v1/vito/internal/clients/search", body),
  });
}

/**
 * Retrieve the full internal record for a client.
 * Requires high assurance/internal permissions.
 */
export function useInternalClientFull(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "internal-search", healthId],
    queryFn: () =>
      apiClient.get<ApiResponse<InternalClientFull>>(
        `/internal/v1/vito/internal/clients/${healthId}/full`
      ),
    enabled: !!healthId,
  });
}
