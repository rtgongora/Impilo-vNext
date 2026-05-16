"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { CoreTransactionBffView } from "../../../../../contracts/core-transaction";

type CoreTransactionView = CoreTransactionBffView;

interface CoreTransactionListData {
  items?: CoreTransactionView[];
}

type AnyRecord = Record<string, unknown>;

function asArray(value: unknown): AnyRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is AnyRecord => typeof item === "object" && item !== null);
  }
  return [];
}

function normalizeCollection(raw: unknown): AnyRecord[] {
  if (Array.isArray(raw)) {
    return raw;
  }
  if (raw && typeof raw === "object") {
    const record = raw as AnyRecord;
    if (Array.isArray(record.items)) return record.items as AnyRecord[];
    if (Array.isArray(record.data)) return record.data as AnyRecord[];
    if (Array.isArray(record.content)) return record.content as AnyRecord[];
    if (Array.isArray(record.tasks)) return record.tasks as AnyRecord[];
  }
  return [];
}

export function useCoreTransactionList(filters?: { state?: string; type?: string }) {
  return useQuery({
    queryKey: ["core-transaction", "list", filters?.state ?? "", filters?.type ?? ""],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.state) params.set("state", filters.state);
      if (filters?.type) params.set("type", filters.type);
      const query = params.toString();
      const url = query
        ? `/internal/v1/core-transactions?${query}`
        : "/internal/v1/core-transactions";
      return apiClient.get<ApiResponse<CoreTransactionListData>>(url);
    },
  });
}

export function useCoreTransactionFeed(filters?: { state?: string; type?: string }) {
  const query = useCoreTransactionList(filters);
  const items = useMemo(
    () => asArray(query.data?.data.items) as CoreTransactionView[],
    [query.data?.data.items],
  );
  return { ...query, items };
}

export function useWorkflowOperatorFeed(status?: string) {
  const query = useQuery({
    queryKey: ["operations", "workflows", status ?? "ALL"],
    queryFn: async () => {
      const qs = status ? `?status=${encodeURIComponent(status)}` : "";
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/workflows${qs}`);
    },
  });

  const items = useMemo(
    () => normalizeCollection(query.data?.data),
    [query.data?.data],
  );

  return { ...query, items };
}

export function useDispatchOperatorFeed(status?: string) {
  const query = useQuery({
    queryKey: ["operations", "dispatch", status ?? "ALL"],
    queryFn: async () => {
      const qs = status ? `?status=${encodeURIComponent(status)}` : "";
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/dispatch/tasks${qs}`);
    },
  });

  const items = useMemo(
    () => normalizeCollection(query.data?.data),
    [query.data?.data],
  );

  return { ...query, items };
}
