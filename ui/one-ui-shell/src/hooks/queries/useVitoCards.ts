"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type CardStatus = "PENDING" | "ACTIVE" | "INACTIVE" | "REVOKED" | "EXPIRED" | "PRINTED";

export type RevocationReason = "LOST" | "STOLEN" | "DAMAGED" | "ERROR" | "REPLACEMENT";

export interface SmartCard {
  cardId: string;
  healthId: string;
  serialNumber?: string;
  status: CardStatus;
  issuedAt?: string;
  expiresAt?: string;
  printedAt?: string;
  activatedAt?: string;
  revokedAt?: string;
  revocationReason?: RevocationReason;
}

interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ── Queries ──────────────────────────────────────────────────────────

export function useActiveCard(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "cards", "active", healthId],
    queryFn: () => apiClient.get<ApiResponse<SmartCard>>(`/internal/v1/vito/cards/active/${healthId}`),
    enabled: !!healthId,
  });
}

export function useCardHistory(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "cards", "history", healthId],
    queryFn: () => apiClient.get<ApiResponse<SmartCard[]>>(`/internal/v1/vito/cards/history/${healthId}`),
    enabled: !!healthId,
  });
}

export function useCardsByStatus(status: CardStatus, page = 0, size = 20) {
  return useQuery({
    queryKey: ["vito", "cards", "by-status", status, page, size],
    queryFn: () =>
      apiClient.get<ApiResponse<PagedItems<SmartCard>>>(
        `/internal/v1/vito/cards/by-status/${status}?page=${page}&size=${size}`
      ),
  });
}

// ── Mutations ────────────────────────────────────────────────────────

export function useRequestCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { healthId: string; reason?: string }) =>
      apiClient.post<ApiResponse<SmartCard>>("/internal/v1/vito/cards/request", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
    },
  });
}

export function useMarkCardPrinted() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      apiClient.post<ApiResponse<SmartCard>>(`/internal/v1/vito/cards/${cardId}/print`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
    },
  });
}

export function useActivateCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      apiClient.post<ApiResponse<SmartCard>>(`/internal/v1/vito/cards/${cardId}/activate`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
    },
  });
}

export function useInactivateCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      apiClient.post<ApiResponse<SmartCard>>(`/internal/v1/vito/cards/${cardId}/inactivate`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
    },
  });
}

export function useRevokeCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ cardId, reason }: { cardId: string; reason: RevocationReason }) =>
      apiClient.post<ApiResponse<SmartCard>>(`/internal/v1/vito/cards/${cardId}/revoke`, { reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
    },
  });
}
