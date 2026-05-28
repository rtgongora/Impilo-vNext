"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { usePrescriptions } from "@/hooks/queries/usePharmacy";

export type RxLiveProbeState = "loading" | "live" | "empty" | "unavailable";

function countRows(payload: unknown): number {
  if (payload == null) return 0;
  if (Array.isArray(payload)) return payload.length;
  if (typeof payload === "object") {
    const row = payload as Record<string, unknown>;
    if (Array.isArray(row.items)) return row.items.length;
    if (Array.isArray(row.content)) return row.content.length;
  }
  return payload ? 1 : 0;
}

function probeState(isLoading: boolean, isError: boolean, payload: unknown): RxLiveProbeState {
  if (isLoading) return "loading";
  if (isError) return "unavailable";
  return countRows(payload) > 0 ? "live" : "empty";
}

function probeLabel(state: RxLiveProbeState, noun: string): string {
  switch (state) {
    case "loading":
      return `${noun}: checking…`;
    case "live":
      return `${noun}: live`;
    case "empty":
      return `${noun}: route live (no rows)`;
    default:
      return `${noun}: unavailable`;
  }
}

export function useRxJourneyLiveStatus(patientId: string | null) {
  const rxQuery = usePrescriptions(patientId ? { patientId } : undefined);

  const payQuery = useQuery({
    queryKey: ["rx-journey", "finance-billing"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/finance/billing?page=0&size=1"),
    staleTime: 30_000,
    retry: false,
  });

  const dispatchQuery = useQuery({
    queryKey: ["rx-journey", "dispatch-deliveries"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/dispatch/deliveries"),
    staleTime: 30_000,
    retry: false,
  });

  const nhumeQuery = useQuery({
    queryKey: ["rx-journey", "nhume-deliveries"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/nhume/deliveries"),
    staleTime: 30_000,
    retry: false,
  });

  const ndilaQuery = useQuery({
    queryKey: ["rx-journey", "ndila-tiles"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/ndila/tiles/config"),
    staleTime: 60_000,
    retry: false,
  });

  const rx = probeState(rxQuery.isLoading, rxQuery.isError, rxQuery.data?.data);
  const pay = probeState(payQuery.isLoading, payQuery.isError, payQuery.data?.data);
  const dispatch = probeState(dispatchQuery.isLoading, dispatchQuery.isError, dispatchQuery.data?.data);
  const nhume = probeState(nhumeQuery.isLoading, nhumeQuery.isError, nhumeQuery.data?.data);
  const ndila = probeState(ndilaQuery.isLoading, ndilaQuery.isError, ndilaQuery.data?.data);

  const delivery = nhume === "live" || nhume === "empty" ? nhume : dispatch;

  return {
    rx,
    pay,
    dispatch,
    nhume,
    ndila,
    delivery,
    labels: {
      rx: probeLabel(rx, "Pharmacy (Rx)"),
      pay: probeLabel(pay, "Finance (MusheX/COSTA)"),
      dispatch: probeLabel(delivery, "Dispatch (Nhume)"),
      ndila: probeLabel(ndila, "Ndila routing"),
    },
    isProbing: rxQuery.isLoading || payQuery.isLoading || dispatchQuery.isLoading || nhumeQuery.isLoading,
  };
}
