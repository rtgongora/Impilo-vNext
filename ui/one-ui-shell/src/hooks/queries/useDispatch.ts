"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type DispatchRecord = Record<string, unknown>;

function asRows(raw: unknown): DispatchRecord[] {
  if (Array.isArray(raw)) return raw.filter((x): x is DispatchRecord => typeof x === "object" && x !== null);
  if (raw && typeof raw === "object") {
    const r = raw as Record<string, unknown>;
    if (Array.isArray(r.items)) return r.items as DispatchRecord[];
    if (Array.isArray(r.content)) return r.content as DispatchRecord[];
    if (Array.isArray(r.deliveries)) return r.deliveries as DispatchRecord[];
    if (Array.isArray(r.tasks)) return r.tasks as DispatchRecord[];
    if (Array.isArray(r.data)) return r.data as DispatchRecord[];
  }
  return [];
}

export function useDispatchTasks(filters?: { status?: string; assigneeId?: string }) {
  return useQuery({
    queryKey: ["dispatch", "tasks", filters?.status ?? "", filters?.assigneeId ?? ""],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.status) params.set("status", filters.status);
      if (filters?.assigneeId) params.set("assigneeId", filters.assigneeId);
      const qs = params.toString() ? `?${params.toString()}` : "";
      const res = await apiClient.get<ApiResponse<unknown>>(`/internal/v1/dispatch/tasks${qs}`);
      return asRows(res.data);
    },
  });
}

export function useDispatchDeliveries() {
  return useQuery({
    queryKey: ["dispatch", "deliveries"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/deliveries");
      return asRows(res.data);
    },
  });
}

export function useDispatchDelivery(id: string) {
  return useQuery({
    queryKey: ["dispatch", "delivery", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<DispatchRecord>>(
        `/internal/v1/dispatch/deliveries/${encodeURIComponent(id)}`,
      );
      return res.data ?? {};
    },
    enabled: id.length > 0,
  });
}

export function useDispatchDeliveryTracking(id: string) {
  return useQuery({
    queryKey: ["dispatch", "delivery-tracking", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/dispatch/deliveries/${encodeURIComponent(id)}/tracking`,
      );
      return res.data;
    },
    enabled: id.length > 0,
  });
}

export function useCreateDispatchDelivery() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DispatchRecord) =>
      apiClient.post<ApiResponse<DispatchRecord>>("/internal/v1/dispatch/deliveries", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["dispatch", "deliveries"] }),
  });
}

export function usePatchDispatchDelivery() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: DispatchRecord }) =>
      apiClient.patch<ApiResponse<DispatchRecord>>(
        `/internal/v1/dispatch/deliveries/${encodeURIComponent(id)}`,
        body,
      ),
    onSuccess: (_d, v) => {
      void qc.invalidateQueries({ queryKey: ["dispatch", "deliveries"] });
      void qc.invalidateQueries({ queryKey: ["dispatch", "delivery", v.id] });
    },
  });
}

export function useDispatchDeliveryAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: DispatchRecord }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/dispatch/deliveries/${encodeURIComponent(id)}/${encodeURIComponent(action)}`,
        body ?? {},
      ),
    onSuccess: (_d, v) => {
      void qc.invalidateQueries({ queryKey: ["dispatch", "deliveries"] });
      void qc.invalidateQueries({ queryKey: ["dispatch", "delivery", v.id] });
    },
  });
}

export function useDispatchDashboard() {
  return useQuery({
    queryKey: ["dispatch", "dashboard"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/dashboard");
      return (res.data ?? {}) as DispatchRecord;
    },
  });
}

export function useDispatchFleet() {
  return useQuery({
    queryKey: ["dispatch", "fleet"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/fleet");
      return asRows(res.data);
    },
  });
}

export function useDispatchFleetAsset(id: string) {
  return useQuery({
    queryKey: ["dispatch", "fleet", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<DispatchRecord>>(
        `/internal/v1/dispatch/fleet/${encodeURIComponent(id)}`,
      );
      return res.data ?? {};
    },
    enabled: id.length > 0,
  });
}

export function useCreateDispatchFleetAsset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DispatchRecord) =>
      apiClient.post<ApiResponse<DispatchRecord>>("/internal/v1/dispatch/fleet", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["dispatch", "fleet"] }),
  });
}

export function useDispatchCouriers() {
  return useQuery({
    queryKey: ["dispatch", "couriers"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/couriers");
      return asRows(res.data);
    },
  });
}

export function useDispatchCourier(id: string) {
  return useQuery({
    queryKey: ["dispatch", "courier", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<DispatchRecord>>(
        `/internal/v1/dispatch/couriers/${encodeURIComponent(id)}`,
      );
      return res.data ?? {};
    },
    enabled: id.length > 0,
  });
}

export function useDispatchPolicies() {
  return useQuery({
    queryKey: ["dispatch", "policies"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/policies");
      return asRows(res.data);
    },
  });
}

export function useDispatchMissions() {
  return useQuery({
    queryKey: ["dispatch", "missions"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/autonomous-missions");
      return asRows(res.data);
    },
  });
}
