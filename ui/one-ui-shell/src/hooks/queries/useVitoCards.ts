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

// ── Citizen self-service (CJ8) — actor-resolved, no card id supplied ──

export interface ReportLostResult {
  status: "REPORTED" | "NO_ACTIVE_CARD";
  reason: "LOST" | "STOLEN";
  revokedCardId?: number;
  replacementRequested: boolean;
  replacement?: unknown;
  message?: string;
}

/**
 * Citizen "report my card lost/stolen" (CJ8). The BFF resolves the caller's own
 * active card server-side from X-Actor-ID — no card id is supplied here.
 */
export function useReportLostCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { reason: "LOST" | "STOLEN"; requestReplacement: boolean }) =>
      apiClient.post<ApiResponse<ReportLostResult>>("/internal/v1/wallet/cards/report-lost", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "cards"] });
      void queryClient.invalidateQueries({ queryKey: ["wallet"] });
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
