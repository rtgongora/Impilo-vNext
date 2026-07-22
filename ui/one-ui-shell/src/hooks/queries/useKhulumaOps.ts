"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Khuluma operations data layer — escalation queue, on-call roster, and omnichannel
 * delivery adapters. These BFF endpoints (`/internal/v1/khuluma/{escalations,on-call,
 * delivery/adapters}`) were already live in khuluma-service + KhulumaBffController with
 * NO frontend consumer; this hook surfaces them for the ops rail of the work-persona hub.
 */

const BASE = "/internal/v1/khuluma";

export function useEscalationQueue(enabled = true) {
  return useQuery({
    queryKey: ["khuluma-escalations"],
    queryFn: () => apiClient.get<unknown>(`${BASE}/escalations`),
    enabled,
    refetchInterval: 30_000,
  });
}

export function useOnCall(enabled = true) {
  return useQuery({
    queryKey: ["khuluma-on-call"],
    queryFn: () => apiClient.get<unknown>(`${BASE}/on-call`),
    enabled,
  });
}

export function useDeliveryAdapters(enabled = true) {
  return useQuery({
    queryKey: ["khuluma-delivery-adapters"],
    queryFn: () => apiClient.get<unknown>(`${BASE}/delivery/adapters`),
    enabled,
  });
}

/** Escalation lifecycle actions (assign/accept/resolve/escalate/cancel) — real state transitions. */
export function useEscalationAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/escalations/${id}/${action}`, body ?? {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["khuluma-escalations"] }),
  });
}
