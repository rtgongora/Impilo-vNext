import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type {
  SmartCard,
  CardStatus,
  CardRequestPayload,
  GenericResponse,
  InactivationReason,
} from "@/types/vito";

export const cardKeys = {
  all: ["cards"] as const,
  byStatus: (status: CardStatus) => [...cardKeys.all, "status", status] as const,
  history: (healthId: string) => [...cardKeys.all, "history", healthId] as const,
};

export function useCardsByStatus(status: CardStatus) {
  return useQuery({
    queryKey: cardKeys.byStatus(status),
    queryFn: () => vitoApiClient.get<SmartCard[]>(`/v1/cards/by-status/${status}`),
    staleTime: 30_000,
  });
}

export function useCardHistory(healthId: string) {
  return useQuery({
    queryKey: cardKeys.history(healthId),
    queryFn: () => vitoApiClient.get<SmartCard[]>(`/v1/cards/history/${healthId}`),
    enabled: !!healthId,
    staleTime: 30_000,
  });
}

export function useRequestCard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CardRequestPayload) =>
      vitoApiClient.post<SmartCard>("/v1/cards/request", payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cardKeys.all });
    },
  });
}

export function usePrintCard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      vitoApiClient.post<SmartCard>(`/v1/cards/${cardId}/print`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cardKeys.all });
    },
  });
}

export function useActivateCard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      vitoApiClient.post<SmartCard>(`/v1/cards/${cardId}/activate`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cardKeys.all });
    },
  });
}

export function useInactivateCard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      cardId,
      reason,
    }: {
      cardId: string;
      reason?: InactivationReason;
    }) =>
      vitoApiClient.post<GenericResponse>(`/v1/cards/${cardId}/inactivate`, { reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cardKeys.all });
    },
  });
}

export function useRevokeCard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      cardId,
      revocationReason,
    }: {
      cardId: string;
      revocationReason: string;
    }) =>
      vitoApiClient.post<GenericResponse>(`/v1/cards/${cardId}/revoke`, { revocationReason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cardKeys.all });
    },
  });
}
