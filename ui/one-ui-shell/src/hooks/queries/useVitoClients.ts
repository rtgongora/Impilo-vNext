"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Identity Status for VITO clients.
 */
export type IdentityStatus = "ACTIVE" | "INACTIVE" | "DECEASED" | "PROVISIONAL" | "PENDING_VERIFICATION";

/**
 * Client Record from VITO registry.
 */
export interface ClientRecord {
  healthId: string;
  impiloId?: string;
  status: IdentityStatus;
  assuranceLevel: string;
  demographics: {
    givenName: string;
    familyName: string;
    dateOfBirth: string;
    sex: string;
  };
  createdAt: string;
  updatedAt: string;
}

/**
 * List clients from VITO registry.
 */
export function useClientList(status?: IdentityStatus, page = 0, size = 20) {
  return useQuery({
    queryKey: ["vito", "clients", "list", { status, page, size }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (status) params.set("status", status);
      params.set("page", page.toString());
      params.set("size", size.toString());
      return apiClient.get<ApiResponse<ClientRecord[]>>(`/api/v1/clients?${params.toString()}`);
    },
  });
}

/**
 * Get detailed client record by Health ID.
 */
export function useClientDetail(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "clients", "detail", healthId],
    queryFn: () => apiClient.get<ApiResponse<ClientRecord>>(`/api/v1/clients/${healthId}`),
    enabled: !!healthId,
  });
}

/**
 * Verify a client identity.
 */
export function useVerifyClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (healthId: string) =>
      apiClient.post<ApiResponse<ClientRecord>>(`/api/v1/clients/${healthId}/verify`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "clients"] });
      void queryClient.invalidateQueries({ queryKey: ["identity"] });
    },
  });
}

/**
 * Deactivate a client record with a reason.
 */
export function useDeactivateClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, reason }: { healthId: string; reason: string }) =>
      apiClient.post<ApiResponse<ClientRecord>>(
        `/api/v1/clients/${healthId}/deactivate?reason=${encodeURIComponent(reason)}`,
        {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "clients"] });
      void queryClient.invalidateQueries({ queryKey: ["identity"] });
    },
  });
}

/**
 * Mark a client as deceased with a notification reference.
 */
export function useMarkDeceased() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, deathNotificationRef }: { healthId: string; deathNotificationRef: string }) =>
      apiClient.post<ApiResponse<ClientRecord>>(
        `/api/v1/clients/${healthId}/deceased?deathNotificationRef=${encodeURIComponent(deathNotificationRef)}`,
        {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "clients"] });
      void queryClient.invalidateQueries({ queryKey: ["identity"] });
    },
  });
}
