"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface UbomiNotificationRow {
  id?: number;
  notificationNumber?: string;
  status?: string;
  childGivenNames?: string;
  childSurname?: string;
  deceasedName?: string;
  dateOfBirth?: string;
  dateOfDeath?: string;
  civilRegNumber?: string;
  [key: string]: unknown;
}

function asRows(raw: unknown): UbomiNotificationRow[] {
  if (Array.isArray(raw)) return raw as UbomiNotificationRow[];
  if (raw && typeof raw === "object") {
    const r = raw as Record<string, unknown>;
    if (Array.isArray(r.content)) return r.content as UbomiNotificationRow[];
    if (Array.isArray(r.items)) return r.items as UbomiNotificationRow[];
  }
  return [];
}

export function useUbomiBirths(page = 0, size = 20) {
  return useQuery({
    queryKey: ["ubomi", "births", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/ubomi/births?page=${page}&size=${size}`,
      );
      return asRows(res.data);
    },
  });
}

export function useUbomiDeaths(page = 0, size = 20) {
  return useQuery({
    queryKey: ["ubomi", "deaths", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/ubomi/deaths?page=${page}&size=${size}`,
      );
      return asRows(res.data);
    },
  });
}

export function useSubmitUbomiBirth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/ubomi/births", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["ubomi", "births"] }),
  });
}

export function useApproveUbomiBirth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.post<ApiResponse<unknown>>(`/internal/v1/ubomi/births/${id}/approve`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["ubomi", "births"] }),
  });
}

export function useSubmitUbomiDeath() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/ubomi/deaths", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["ubomi", "deaths"] }),
  });
}

export function useCertifyUbomiDeath() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.post<ApiResponse<unknown>>(`/internal/v1/ubomi/deaths/${id}/certify`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["ubomi", "deaths"] }),
  });
}

export function useUbomiVerification(registrationNumber: string) {
  return useQuery({
    queryKey: ["ubomi", "verify", registrationNumber],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/ubomi/verifications/${encodeURIComponent(registrationNumber)}`,
      ),
    enabled: registrationNumber.trim().length > 2,
  });
}
