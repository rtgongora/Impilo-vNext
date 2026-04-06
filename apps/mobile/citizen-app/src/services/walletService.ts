/**
 * Health Wallet Service — Balance and transaction management.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { HealthWallet, WalletTransaction } from "../types";

const V1 = "/internal/v1/mobile/citizen/wallet";

export async function fetchWallet(patientId: string): Promise<HealthWallet> {
  const response = await apiClient.get<{ data: HealthWallet }>(`${V1}?patientId=${patientId}`);
  return response.data.data;
}

export async function fetchTransactions(patientId: string): Promise<WalletTransaction[]> {
  const response = await apiClient.get<{ data: WalletTransaction[] }>(`${V1}/transactions?patientId=${patientId}`);
  return response.data.data;
}
