import { apiClient } from "@impilo/mobile-api-client";

export type CoreTransactionRecord = Record<string, unknown>;

function normalizeItems(value: unknown): CoreTransactionRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is CoreTransactionRecord => typeof item === "object" && item !== null);
  }
  if (typeof value === "object" && value !== null) {
    const record = value as CoreTransactionRecord;
    if (Array.isArray(record.items)) return normalizeItems(record.items);
    if (Array.isArray(record.data)) return normalizeItems(record.data);
  }
  return [];
}

export async function listCoreTransactions(filters?: { state?: string; type?: string }) {
  const params = new URLSearchParams();
  if (filters?.state) params.set("state", filters.state);
  if (filters?.type) params.set("type", filters.type);
  const qs = params.toString();
  const path = qs ? `/internal/v1/core-transactions?${qs}` : "/internal/v1/core-transactions";
  const response = await apiClient.get<{ data?: unknown }>(path);
  return normalizeItems(response.data?.data);
}

export async function applyCoreTransactionAction(
  transactionId: string,
  actionCode: string,
  payload?: CoreTransactionRecord,
) {
  const response = await apiClient.post<{ data?: unknown }>(
    `/internal/v1/core-transactions/${encodeURIComponent(transactionId)}/actions/${encodeURIComponent(actionCode)}`,
    payload ?? {},
  );
  return response.data?.data;
}

export async function requestNompiloHandoff(transactionId: string, payload?: CoreTransactionRecord) {
  const response = await apiClient.post<{ data?: unknown }>(
    `/internal/v1/core-transactions/${encodeURIComponent(transactionId)}/nompilo/handoff`,
    payload ?? { handoffType: "CALL_CENTER" },
  );
  return response.data?.data;
}

export async function fetchNompiloContext(transactionId: string) {
  const response = await apiClient.get<{ data?: { nompilo?: CoreTransactionRecord } }>(
    `/internal/v1/core-transactions/${encodeURIComponent(transactionId)}/nompilo`,
  );
  const envelope = response.data?.data as CoreTransactionRecord | undefined;
  return (envelope?.nompilo as CoreTransactionRecord | undefined) ?? envelope ?? {};
}
