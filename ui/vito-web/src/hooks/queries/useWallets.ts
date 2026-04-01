import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { Wallet, WalletJournal, WalletTransactionPayload, PagedResponse } from "@/types/vito";

export interface WalletJournalParams {
  walletId: string;
  page?: number;
  size?: number;
}

export const walletKeys = {
  all: ["wallets"] as const,
  detail: (healthId: string) => [...walletKeys.all, "detail", healthId] as const,
  journal: (walletId: string, params?: { page?: number; size?: number }) =>
    [...walletKeys.all, "journal", walletId, params] as const,
};

export function useWallet(healthId: string) {
  return useQuery({
    queryKey: walletKeys.detail(healthId),
    queryFn: () => vitoApiClient.get<Wallet>(`/v1/wallet/${healthId}`),
    enabled: !!healthId,
    staleTime: 30_000,
  });
}

export function useWalletJournal(params: WalletJournalParams) {
  return useQuery({
    queryKey: walletKeys.journal(params.walletId, { page: params.page, size: params.size }),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<WalletJournal>>(
        `/v1/wallet/${params.walletId}/journal${qs ? `?${qs}` : ""}`
      );
    },
    enabled: !!params.walletId,
    staleTime: 30_000,
  });
}

export function useCreateWallet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (healthId: string) =>
      vitoApiClient.post<Wallet>("/v1/wallet/create", { healthId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: walletKeys.all });
    },
  });
}

export function useTopUpWallet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: WalletTransactionPayload) =>
      vitoApiClient.post<Wallet>("/v1/wallet/topup", payload),
    onSuccess: (_, payload) => {
      qc.invalidateQueries({ queryKey: walletKeys.detail(payload.healthId) });
    },
  });
}

export function usePayWallet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: WalletTransactionPayload) =>
      vitoApiClient.post<Wallet>("/v1/wallet/pay", payload),
    onSuccess: (_, payload) => {
      qc.invalidateQueries({ queryKey: walletKeys.detail(payload.healthId) });
    },
  });
}
