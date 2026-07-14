/**
 * Citizen virtual-care lane (Lane E Wave 1) — governed virtual-hospital discovery with
 * HONEST availability, teleconsult pool requests, and the caller's own request list.
 * Everything is fetched via the BFF (/internal/v1/virtual-care/**); the patient anchor is
 * the caller's X-Actor-ID injected by api-client — never a client-supplied patient id.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface VirtualHospitalCard {
  id: string;
  name: string;
  level?: string;
  purpose?: string;
  catchment?: string;
  serviceLines?: string[];
  availability: "REQUESTABLE" | "COMING_SOON";
  poolId?: string;
}

export interface VirtualCareRequestRow {
  id: string;
  status?: string;
  specialty?: string;
  reason?: string;
  modality?: string;
  poolId?: string;
  virtualHospital?: { id: string; name: string } | null;
  createdAt?: string;
  submittedAt?: string;
  scheduledAt?: string;
}

export interface CreateVirtualCareRequestInput {
  virtualHospitalId: string;
  reason: string;
  modality: "video" | "audio" | "message";
  consentGranted: boolean;
}

export interface VirtualCareRequestConfirmation {
  requestId: string;
  status: string;
  virtualHospital?: { id: string; name: string };
  poolId?: string;
  modality?: string;
  message?: string;
}

export function useVirtualHospitals(audience: "citizen" | "provider" = "citizen") {
  return useQuery({
    queryKey: ["virtual-care", "virtual-hospitals", audience],
    queryFn: () =>
      apiClient.get<ApiResponse<{ audience: string; virtualHospitals: VirtualHospitalCard[] }>>(
        `/internal/v1/virtual-care/virtual-hospitals?audience=${audience}`,
      ),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreateVirtualCareRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateVirtualCareRequestInput) =>
      apiClient.post<ApiResponse<VirtualCareRequestConfirmation>>(
        "/internal/v1/virtual-care/requests",
        input,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["virtual-care", "my-requests"] });
    },
  });
}

export function useMyVirtualCareRequests() {
  return useQuery({
    queryKey: ["virtual-care", "my-requests"],
    queryFn: () =>
      apiClient.get<ApiResponse<{ requests: VirtualCareRequestRow[] }>>(
        "/internal/v1/virtual-care/requests/mine",
      ),
  });
}

/**
 * Provider view of a virtual-hospital specialty-pool queue (governed BFF read of PCT's
 * pool-scoped worklist). Rows are teleconsult referrals — the existing accept/decline
 * session actions apply to them.
 */
export function useTeleconsultPoolQueue(poolId?: string | null, status?: string) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return useQuery({
    queryKey: ["virtual-care", "pool-queue", poolId ?? "none", status ?? "default"],
    enabled: Boolean(poolId),
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/teleconsult/pool/${encodeURIComponent(poolId as string)}/queue${qs}`,
      ),
    refetchInterval: 30_000,
  });
}
