/**
 * Experience UI — Mushe Wallet (Finance Plane) Query Hooks
 *
 * Covers wallet CRUD, transactions, merchant payments, holds,
 * funding sources, deposits, and smart card lifecycle management.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type WalletResponse = ApiResponse<unknown>;
type TransactionResponse = ApiResponse<unknown>;
type MerchantResponse = ApiResponse<unknown>;
type HoldResponse = ApiResponse<unknown>;
type FundingSourceResponse = ApiResponse<unknown>;
type CardResponse = ApiResponse<unknown>;
type CardHealthDataResponse = ApiResponse<unknown>;

// ── Wallet Queries ─────────────────────────────────────────────────

export function useWallet(walletId?: string | null) {
  return useQuery<WalletResponse>({
    queryKey: ["mushe-wallet", walletId],
    queryFn: () =>
      apiClient.get<WalletResponse>(`/internal/v1/wallets/${walletId}`),
    enabled: !!walletId,
  });
}

export function useWalletByOwner(ownerType?: string | null, ownerRef?: string | null) {
  return useQuery<WalletResponse>({
    queryKey: ["mushe-wallet-by-owner", ownerType, ownerRef],
    queryFn: () =>
      apiClient.get<WalletResponse>(
        `/internal/v1/wallets/by-owner?owner_type=${ownerType}&owner_ref=${ownerRef}`,
      ),
    enabled: !!ownerType && !!ownerRef,
  });
}

export function useBalance(walletId?: string | null) {
  return useQuery<WalletResponse>({
    queryKey: ["mushe-wallet-balance", walletId],
    queryFn: () =>
      apiClient.get<WalletResponse>(`/internal/v1/wallets/${walletId}/balance`),
    enabled: !!walletId,
  });
}

export function useTransactions(walletId?: string | null, page?: number, size?: number) {
  const params = new URLSearchParams();
  if (page != null) params.set("page", String(page));
  if (size != null) params.set("size", String(size));
  const qs = params.toString();

  return useQuery<TransactionResponse>({
    queryKey: ["mushe-wallet-transactions", walletId, page ?? 0, size ?? 20],
    queryFn: () =>
      apiClient.get<TransactionResponse>(
        `/internal/v1/wallets/${walletId}/transactions${qs ? `?${qs}` : ""}`,
      ),
    enabled: !!walletId,
  });
}

// ── Merchant Queries ───────────────────────────────────────────────

export function useMerchant(merchantId?: string | null) {
  return useQuery<MerchantResponse>({
    queryKey: ["mushe-merchant", merchantId],
    queryFn: () =>
      apiClient.get<MerchantResponse>(`/internal/v1/merchants/${merchantId}`),
    enabled: !!merchantId,
  });
}

export function useMerchantByProvider(providerNumber?: string | null) {
  return useQuery<MerchantResponse>({
    queryKey: ["mushe-merchant-by-provider", providerNumber],
    queryFn: () =>
      apiClient.get<MerchantResponse>(
        `/internal/v1/merchants/by-provider?provider_number=${providerNumber}`,
      ),
    enabled: !!providerNumber,
  });
}

// ── Card Queries ───────────────────────────────────────────────────

export function useCards(walletId?: string | null) {
  return useQuery<CardResponse>({
    queryKey: ["mushe-cards", walletId],
    queryFn: () =>
      apiClient.get<CardResponse>(`/internal/v1/cards/by-wallet/${walletId}`),
    enabled: !!walletId,
  });
}

export function useCard(cardId?: string | null) {
  return useQuery<CardResponse>({
    queryKey: ["mushe-card", cardId],
    queryFn: () =>
      apiClient.get<CardResponse>(`/internal/v1/cards/${cardId}`),
    enabled: !!cardId,
  });
}

export function useCardHealthData(cardId?: string | null) {
  return useQuery<CardHealthDataResponse>({
    queryKey: ["mushe-card-health-data", cardId],
    queryFn: () =>
      apiClient.get<CardHealthDataResponse>(`/internal/v1/cards/${cardId}/health-data`),
    enabled: !!cardId,
  });
}

// ── Funding Source Queries ─────────────────────────────────────────

export function useFundingSources(walletId?: string | null) {
  return useQuery<FundingSourceResponse>({
    queryKey: ["mushe-funding-sources", walletId],
    queryFn: () =>
      apiClient.get<FundingSourceResponse>(`/internal/v1/funding/${walletId}/sources`),
    enabled: !!walletId,
  });
}

// ── Wallet Mutations ───────────────────────────────────────────────

export function useCreateWallet() {
  const queryClient = useQueryClient();
  return useMutation<WalletResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<WalletResponse>("/internal/v1/wallets", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-by-owner"] });
    },
  });
}

export function useCreditWallet() {
  const queryClient = useQueryClient();
  return useMutation<TransactionResponse, unknown, { walletId: string; body: Record<string, unknown> }>({
    mutationFn: ({ walletId, body }) =>
      apiClient.post<TransactionResponse>(`/internal/v1/wallets/${walletId}/credit`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

export function useDebitWallet() {
  const queryClient = useQueryClient();
  return useMutation<TransactionResponse, unknown, { walletId: string; body: Record<string, unknown> }>({
    mutationFn: ({ walletId, body }) =>
      apiClient.post<TransactionResponse>(`/internal/v1/wallets/${walletId}/debit`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

export function useTransfer() {
  const queryClient = useQueryClient();
  return useMutation<TransactionResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<TransactionResponse>("/internal/v1/wallets/transfer", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

// ── Merchant Mutations ─────────────────────────────────────────────

export function useRegisterMerchant() {
  const queryClient = useQueryClient();
  return useMutation<MerchantResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<MerchantResponse>("/internal/v1/merchants", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-merchant"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-merchant-by-provider"] });
    },
  });
}

export function usePayMerchant() {
  const queryClient = useQueryClient();
  return useMutation<TransactionResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<TransactionResponse>("/internal/v1/merchants/pay", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

// ── Hold Mutations ─────────────────────────────────────────────────

export function usePlaceHold() {
  const queryClient = useQueryClient();
  return useMutation<HoldResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<HoldResponse>("/internal/v1/holds", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
    },
  });
}

export function useCaptureHold() {
  const queryClient = useQueryClient();
  return useMutation<HoldResponse, unknown, { holdId: string }>({
    mutationFn: ({ holdId }) =>
      apiClient.post<HoldResponse>(`/internal/v1/holds/${holdId}/capture`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

export function useReleaseHold() {
  const queryClient = useQueryClient();
  return useMutation<HoldResponse, unknown, { holdId: string }>({
    mutationFn: ({ holdId }) =>
      apiClient.post<HoldResponse>(`/internal/v1/holds/${holdId}/release`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
    },
  });
}

// ── Funding Source Mutations ───────────────────────────────────────

export function useAddFundingSource() {
  const queryClient = useQueryClient();
  return useMutation<FundingSourceResponse, unknown, { walletId: string; body: Record<string, unknown> }>({
    mutationFn: ({ walletId, body }) =>
      apiClient.post<FundingSourceResponse>(`/internal/v1/funding/${walletId}/sources`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-funding-sources"] });
    },
  });
}

export function useDeposit() {
  const queryClient = useQueryClient();
  return useMutation<TransactionResponse, unknown, { walletId: string; body: Record<string, unknown> }>({
    mutationFn: ({ walletId, body }) =>
      apiClient.post<TransactionResponse>(`/internal/v1/funding/${walletId}/deposit`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

export function useCashDeposit() {
  const queryClient = useQueryClient();
  return useMutation<TransactionResponse, unknown, { walletId: string; body: Record<string, unknown> }>({
    mutationFn: ({ walletId, body }) =>
      apiClient.post<TransactionResponse>(`/internal/v1/funding/${walletId}/cash-deposit`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

// ── Card Mutations ─────────────────────────────────────────────────

export function useIssueCard() {
  const queryClient = useQueryClient();
  return useMutation<CardResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<CardResponse>("/internal/v1/cards", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-cards"] });
    },
  });
}

export function useActivateCard() {
  const queryClient = useQueryClient();
  return useMutation<CardResponse, unknown, { cardId: string; body: Record<string, unknown> }>({
    mutationFn: ({ cardId, body }) =>
      apiClient.post<CardResponse>(`/internal/v1/cards/${cardId}/activate`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-card"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-cards"] });
    },
  });
}

export function useBlockCard() {
  const queryClient = useQueryClient();
  return useMutation<CardResponse, unknown, { cardId: string; body?: Record<string, unknown> }>({
    mutationFn: ({ cardId, body }) =>
      apiClient.post<CardResponse>(`/internal/v1/cards/${cardId}/block`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-card"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-cards"] });
    },
  });
}

export function useUnblockCard() {
  const queryClient = useQueryClient();
  return useMutation<CardResponse, unknown, { cardId: string }>({
    mutationFn: ({ cardId }) =>
      apiClient.post<CardResponse>(`/internal/v1/cards/${cardId}/unblock`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-card"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-cards"] });
    },
  });
}

export function useReplaceCard() {
  const queryClient = useQueryClient();
  return useMutation<CardResponse, unknown, { cardId: string }>({
    mutationFn: ({ cardId }) =>
      apiClient.post<CardResponse>(`/internal/v1/cards/${cardId}/replace`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-card"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-cards"] });
    },
  });
}

export function useSyncHealthData() {
  const queryClient = useQueryClient();
  return useMutation<CardHealthDataResponse, unknown, { cardId: string }>({
    mutationFn: ({ cardId }) =>
      apiClient.post<CardHealthDataResponse>(`/internal/v1/cards/${cardId}/health-data/sync`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-card-health-data"] });
    },
  });
}
