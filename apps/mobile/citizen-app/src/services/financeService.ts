/**
 * Finance Service — Balance, transactions, and pending charges.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/finance/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Transaction, Balance, PendingCharge } from "../types";

const V1 = "/internal/v1/mobile/citizen/finance";

export async function fetchTransactions(params: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
} = {}): Promise<Transaction[]> {
  const query: string[] = [];
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  if (params.from) query.push(`from=${encodeURIComponent(params.from)}`);
  if (params.to) query.push(`to=${encodeURIComponent(params.to)}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<{ data: Transaction[] }>(`${V1}/transactions${qs}`);
  return response.data.data ?? [];
}

export async function fetchBalance(): Promise<Balance> {
  const response = await apiClient.get<{ data: Balance }>(`${V1}/balance`);
  return response.data.data;
}

export async function fetchPendingCharges(): Promise<PendingCharge[]> {
  const response = await apiClient.get<{ data: PendingCharge[] }>(`${V1}/charges?status=PENDING`);
  return response.data.data ?? [];
}
