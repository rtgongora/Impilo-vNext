"use client";

import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type AnyRecord = Record<string, unknown>;

function normalizeCollection(raw: unknown): AnyRecord[] {
  if (Array.isArray(raw)) {
    return raw.filter((item): item is AnyRecord => typeof item === "object" && item !== null);
  }
  if (raw && typeof raw === "object") {
    const record = raw as AnyRecord;
    if (Array.isArray(record.items)) return normalizeCollection(record.items);
    if (Array.isArray(record.data)) return normalizeCollection(record.data);
    if (Array.isArray(record.content)) return normalizeCollection(record.content);
  }
  return [];
}

function invalidateIdentity(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["identity-operations"] });
  void queryClient.invalidateQueries({ queryKey: ["client-registry"] });
}

export function useIdentitySearch(query: string) {
  const trimmed = query.trim();
  const result = useQuery({
    queryKey: ["identity-operations", "search", trimmed],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/identity/search?query=${encodeURIComponent(trimmed)}`,
      ),
    enabled: trimmed.length >= 2,
  });

  const items = useMemo(() => normalizeCollection(result.data?.data), [result.data?.data]);
  return { ...result, items };
}

export function useResolvePatientIdentity() {
  return useMutation({
    mutationFn: (healthId: string) =>
      apiClient.post<ApiResponse<AnyRecord>>("/internal/v1/identity/patient/resolve", {
        healthId,
      }),
  });
}

export function useRegisterPatientIdentity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AnyRecord) =>
      apiClient.post<ApiResponse<AnyRecord>>("/internal/v1/identity/patient/register", payload),
    onSuccess: () => invalidateIdentity(queryClient),
  });
}

export function useStartPatientRecovery() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AnyRecord) =>
      apiClient.post<ApiResponse<AnyRecord>>("/internal/v1/identity/patient/recovery/start", payload),
    onSuccess: () => invalidateIdentity(queryClient),
  });
}

export function useVerifyPatientRecovery() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AnyRecord) =>
      apiClient.post<ApiResponse<AnyRecord>>("/internal/v1/identity/patient/recovery/verify", payload),
    onSuccess: () => invalidateIdentity(queryClient),
  });
}

export function useCreateProviderIdentity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AnyRecord) =>
      apiClient.post<ApiResponse<AnyRecord>>("/internal/v1/identity/provider/create", payload),
    onSuccess: () => invalidateIdentity(queryClient),
  });
}

export function useProviderIdentity(providerId: string) {
  const trimmed = providerId.trim();
  return useQuery({
    queryKey: ["identity-operations", "provider", trimmed],
    queryFn: () =>
      apiClient.get<ApiResponse<AnyRecord>>(`/internal/v1/identity/provider/${encodeURIComponent(trimmed)}`),
    enabled: trimmed.length > 0,
  });
}
