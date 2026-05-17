import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type Json = Record<string, unknown>;

export function useDispatchDashboard() {
  return useQuery({
    queryKey: ["dispatch", "dashboard"],
    queryFn: () => apiClient.get<Json>("/internal/v1/dispatch/dashboard"),
  });
}

export function useDispatchConsole() {
  return useQuery({
    queryKey: ["dispatch", "console"],
    queryFn: () => apiClient.get<Json>("/internal/v1/dispatch/dispatcher-console"),
  });
}

export function useDispatchDeliveries() {
  return useQuery({
    queryKey: ["dispatch", "deliveries"],
    queryFn: () => apiClient.get<Json>("/internal/v1/dispatch/deliveries"),
  });
}

export function useDispatchFleet() {
  return useQuery({
    queryKey: ["dispatch", "fleet"],
    queryFn: () => apiClient.get<Json>("/internal/v1/dispatch/fleet"),
  });
}

export function useDispatchCouriers() {
  return useQuery({
    queryKey: ["dispatch", "couriers"],
    queryFn: () => apiClient.get<Json>("/internal/v1/dispatch/couriers"),
  });
}

export function useDispatchMissions() {
  return useQuery({
    queryKey: ["dispatch", "missions"],
    queryFn: () => apiClient.get<Json>("/internal/v1/dispatch/autonomous-missions"),
  });
}

export function useCreateDelivery() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: Json) => apiClient.post<Json>("/internal/v1/dispatch/deliveries", payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["dispatch"] });
    },
  });
}
