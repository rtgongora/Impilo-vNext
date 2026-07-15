/**
 * Experience UI — Mushe Wallet (Enterprise Plane, finance domain) Query Hooks
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
  // Route through BFF wallet controller which has local fallbacks
  return useQuery<WalletResponse>({
    queryKey: ["mushe-wallet-by-owner", ownerType, ownerRef],
    queryFn: () => apiClient.get<WalletResponse>(`/internal/v1/wallet/me`),
    enabled: !!ownerType && !!ownerRef,
  });
}

export function useBalance(walletId?: string | null) {
  return useQuery<WalletResponse>({
    queryKey: ["mushe-wallet-balance", walletId],
    queryFn: () => apiClient.get<WalletResponse>(`/internal/v1/wallet/me/balance`),
    enabled: !!walletId,
  });
}

export function useTransactions(walletId?: string | null, page?: number, size?: number) {
  return useQuery<TransactionResponse>({
    queryKey: ["mushe-wallet-transactions", walletId, page ?? 0, size ?? 20],
    queryFn: () => apiClient.get<TransactionResponse>(`/internal/v1/wallet/me/transactions`),
    enabled: !!walletId,
  });
}

export function useFundingSources(walletId?: string | null) {
  return useQuery<FundingSourceResponse>({
    queryKey: ["mushe-funding-sources", walletId],
    queryFn: () => apiClient.get<FundingSourceResponse>(`/internal/v1/wallet/me/funding-sources`),
    enabled: !!walletId,
  });
}

export function useCards(walletId?: string | null) {
  return useQuery<CardResponse>({
    queryKey: ["mushe-cards", walletId],
    queryFn: () => apiClient.get<CardResponse>(`/internal/v1/wallet/me/cards`),
    enabled: !!walletId,
  });
}

/** Universal payment — calls BFF wallet/pay with method selection */
export function useWalletPay() {
  const queryClient = useQueryClient();
  return useMutation<WalletResponse, unknown, { method: string; amount: number; reference?: string; description?: string; currency?: string }>({
    mutationFn: (body) => apiClient.post<WalletResponse>(`/internal/v1/wallet/pay`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-transactions"] });
    },
  });
}

/** Payment methods reference */
export function usePaymentMethods() {
  return useQuery<ApiResponse<Array<{ id: string; label: string; icon: string; description: string; enabled: boolean }>>>({
    queryKey: ["payment-methods"],
    queryFn: () => apiClient.get(`/internal/v1/wallet/payment-methods`),
    staleTime: 5 * 60 * 1000,
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
// useCards is defined above with BFF fallback routing

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
// useFundingSources is defined above with BFF fallback routing

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

/**
 * Deposit intents (pending + confirmed) for a wallet — the citizen's cash-in
 * history. A PENDING deposit shows the reference code to quote with the
 * transfer; the balance rises only once arrival is confirmed (ops/statement).
 */
export function useDeposits(walletId?: string | null) {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["mushe-deposits", walletId],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(`/internal/v1/funding/${walletId}/deposits`),
    enabled: !!walletId,
  });
}

/** Cancel a still-pending deposit the citizen no longer intends to fund. */
export function useCancelDeposit() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { walletId: string; depositId: string }>({
    mutationFn: ({ walletId, depositId }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/funding/${walletId}/deposits/${depositId}/cancel`,
        {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["mushe-deposits"] });
      void queryClient.invalidateQueries({ queryKey: ["mushe-wallet-balance"] });
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
