/**
 * Nhume — React Query hooks
 *
 * Centralises all data access for the Nhume frontend so that pages stay thin
 * and caching/invalidation rules live in one place. Every mutation invalidates
 * the lists it can plausibly affect so users see fresh status the moment
 * dispatchers, couriers or providers act.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  nhumeApi,
  type AssignDeliveryPayload,
  type ChainOfCustodyPayload,
  type CreateDeliveryRequestPayload,
  type DashboardSnapshot,
  type DeliverySummary,
  type DispatcherConsole,
  type ProofPayload,
  type StatusChangePayload,
  type TrackingUpdatePayload,
} from "@/lib/nhume";

export const nhumeQueryKeys = {
  all: ["nhume"] as const,
  dashboard: () => [...nhumeQueryKeys.all, "dashboard"] as const,
  console: () => [...nhumeQueryKeys.all, "dispatcher-console"] as const,
  zones: () => [...nhumeQueryKeys.all, "zones"] as const,
  mapToken: () => [...nhumeQueryKeys.all, "map-token"] as const,
  deliveries: (params?: Record<string, unknown>) =>
    [...nhumeQueryKeys.all, "deliveries", params ?? {}] as const,
  delivery: (id: string) => [...nhumeQueryKeys.all, "deliveries", id] as const,
  timeline: (id: string) => [...nhumeQueryKeys.all, "deliveries", id, "timeline"] as const,
  items: (id: string) => [...nhumeQueryKeys.all, "deliveries", id, "items"] as const,
  tracking: (id: string) => [...nhumeQueryKeys.all, "deliveries", id, "tracking"] as const,
  custody: (id: string) => [...nhumeQueryKeys.all, "deliveries", id, "custody"] as const,
  fleet: () => [...nhumeQueryKeys.all, "fleet"] as const,
  asset: (id: string) => [...nhumeQueryKeys.all, "fleet", id] as const,
  couriers: () => [...nhumeQueryKeys.all, "couriers"] as const,
  courier: (id: string) => [...nhumeQueryKeys.all, "couriers", id] as const,
  policies: () => [...nhumeQueryKeys.all, "policies"] as const,
  integrations: () => [...nhumeQueryKeys.all, "integrations"] as const,
  missions: () => [...nhumeQueryKeys.all, "missions"] as const,
  mission: (id: string) => [...nhumeQueryKeys.all, "missions", id] as const,
};

// ---- Dashboards ----------------------------------------------------------

export function useNhumeDashboard() {
  return useQuery({
    queryKey: nhumeQueryKeys.dashboard(),
    queryFn: () => nhumeApi.dashboard() as Promise<DashboardSnapshot>,
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useNhumeDispatcherConsole() {
  return useQuery({
    queryKey: nhumeQueryKeys.console(),
    queryFn: () => nhumeApi.dispatcherConsole() as Promise<DispatcherConsole>,
    staleTime: 10_000,
    refetchInterval: 20_000,
  });
}

export function useNhumeMapToken() {
  return useQuery({
    queryKey: nhumeQueryKeys.mapToken(),
    queryFn: () => nhumeApi.mapToken(),
    staleTime: 5 * 60_000,
  });
}

export function useNhumeZones() {
  return useQuery({
    queryKey: nhumeQueryKeys.zones(),
    queryFn: () => nhumeApi.zones(),
    staleTime: 5 * 60_000,
  });
}

// ---- Deliveries ----------------------------------------------------------

export function useNhumeDeliveries(params?: {
  status?: string;
  priority?: string;
  q?: string;
  assignedTo?: string;
  size?: number;
}) {
  return useQuery({
    queryKey: nhumeQueryKeys.deliveries(params),
    queryFn: () => nhumeApi.listDeliveries(params) as Promise<{ data: DeliverySummary[] }>,
    staleTime: 10_000,
  });
}

export function useNhumeDelivery(id: string | undefined) {
  return useQuery({
    queryKey: id ? nhumeQueryKeys.delivery(id) : ["nhume", "deliveries", "_disabled"],
    queryFn: () => (id ? nhumeApi.getDelivery(id) : Promise.resolve(null)),
    enabled: Boolean(id),
    staleTime: 5_000,
  });
}

export function useNhumeTimeline(id: string | undefined) {
  return useQuery({
    queryKey: id ? nhumeQueryKeys.timeline(id) : ["nhume", "deliveries", "_disabled", "timeline"],
    queryFn: () => (id ? nhumeApi.getTimeline(id) : Promise.resolve({ data: [] })),
    enabled: Boolean(id),
    staleTime: 5_000,
  });
}

export function useNhumeTracking(id: string | undefined) {
  return useQuery({
    queryKey: id ? nhumeQueryKeys.tracking(id) : ["nhume", "deliveries", "_disabled", "tracking"],
    queryFn: () => (id ? nhumeApi.getTracking(id) : Promise.resolve({ data: [] })),
    enabled: Boolean(id),
    staleTime: 10_000,
    refetchInterval: (q) => {
      // Stop polling for closed-out lifecycles to save battery on mobile.
      const data = (q.state.data as { data?: Array<Record<string, unknown>> } | undefined)?.data ?? [];
      if (data.length > 0) {
        const last = data[data.length - 1];
        const status = (last?.status as string | undefined) ?? "";
        if (["DELIVERED", "FAILED", "CANCELLED", "RETURNED", "CLOSED"].includes(status)) return false;
      }
      return 15_000;
    },
  });
}

export function useNhumeCustody(id: string | undefined) {
  return useQuery({
    queryKey: id ? nhumeQueryKeys.custody(id) : ["nhume", "deliveries", "_disabled", "custody"],
    queryFn: () => (id ? nhumeApi.getCustody(id) : Promise.resolve({ data: [] })),
    enabled: Boolean(id),
    staleTime: 10_000,
  });
}

// ---- Delivery lifecycle mutations ----------------------------------------

export function useCreateNhumeDelivery() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateDeliveryRequestPayload) => nhumeApi.createDelivery(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.all });
    },
  });
}

function invalidateDelivery(qc: ReturnType<typeof useQueryClient>, id: string) {
  qc.invalidateQueries({ queryKey: nhumeQueryKeys.delivery(id) });
  qc.invalidateQueries({ queryKey: nhumeQueryKeys.timeline(id) });
  qc.invalidateQueries({ queryKey: nhumeQueryKeys.tracking(id) });
  qc.invalidateQueries({ queryKey: nhumeQueryKeys.custody(id) });
  qc.invalidateQueries({ queryKey: [...nhumeQueryKeys.all, "deliveries"] });
  qc.invalidateQueries({ queryKey: nhumeQueryKeys.dashboard() });
  qc.invalidateQueries({ queryKey: nhumeQueryKeys.console() });
}

export function useSubmitDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: StatusChangePayload) => nhumeApi.submit(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useApproveDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: StatusChangePayload) => nhumeApi.approve(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useRejectDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StatusChangePayload) => nhumeApi.reject(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useCancelDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StatusChangePayload) => nhumeApi.cancel(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useAssignDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AssignDeliveryPayload) => nhumeApi.assign(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useReassignDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AssignDeliveryPayload) => nhumeApi.reassign(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useAcceptDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: StatusChangePayload) => nhumeApi.accept(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useDeclineDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StatusChangePayload) => nhumeApi.decline(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function usePickupDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: StatusChangePayload) => nhumeApi.pickup(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useStartDeliveryTransit(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: StatusChangePayload) => nhumeApi.startTransit(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useFailDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StatusChangePayload) => nhumeApi.fail(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useReturnDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: StatusChangePayload) => nhumeApi.returnDelivery(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useRecordLocation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: TrackingUpdatePayload) => nhumeApi.recordLocation(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.tracking(id) });
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.delivery(id) });
    },
  });
}

export function useRecordProof(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProofPayload) => nhumeApi.recordProof(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

export function useRecordCustody(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ChainOfCustodyPayload) => nhumeApi.recordCustody(id, body),
    onSuccess: () => invalidateDelivery(qc, id),
  });
}

// ---- Fleet & couriers ----------------------------------------------------

export function useNhumeFleet() {
  return useQuery({
    queryKey: nhumeQueryKeys.fleet(),
    queryFn: () => nhumeApi.listFleet(),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useNhumeAsset(id: string | undefined) {
  return useQuery({
    queryKey: id ? nhumeQueryKeys.asset(id) : ["nhume", "fleet", "_disabled"],
    queryFn: () => (id ? nhumeApi.getAsset(id) : Promise.resolve(null)),
    enabled: Boolean(id),
    staleTime: 10_000,
  });
}

export function useCreateFleetAsset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.createAsset(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: nhumeQueryKeys.fleet() }),
  });
}

export function useUpdateFleetAsset(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.updateAsset(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.fleet() });
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.asset(id) });
    },
  });
}

export function useNhumeCouriers() {
  return useQuery({
    queryKey: nhumeQueryKeys.couriers(),
    queryFn: () => nhumeApi.listCouriers(),
    staleTime: 30_000,
  });
}

export function useNhumeCourier(id: string | undefined) {
  return useQuery({
    queryKey: id ? nhumeQueryKeys.courier(id) : ["nhume", "couriers", "_disabled"],
    queryFn: () => (id ? nhumeApi.getCourier(id) : Promise.resolve(null)),
    enabled: Boolean(id),
    staleTime: 30_000,
  });
}

export function useCreateCourier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.createCourier(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: nhumeQueryKeys.couriers() }),
  });
}

export function useUpdateCourier(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.updateCourier(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.couriers() });
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.courier(id) });
    },
  });
}

// ---- Policies / integrations / missions ----------------------------------

export function useNhumePolicies() {
  return useQuery({
    queryKey: nhumeQueryKeys.policies(),
    queryFn: () => nhumeApi.listPolicies(),
    staleTime: 60_000,
  });
}

export function useCreatePolicy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.createPolicy(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: nhumeQueryKeys.policies() }),
  });
}

export function useNhumeIntegrations() {
  return useQuery({
    queryKey: nhumeQueryKeys.integrations(),
    queryFn: () => nhumeApi.listIntegrations(),
    staleTime: 60_000,
  });
}

export function useCreateIntegration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.createIntegration(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: nhumeQueryKeys.integrations() }),
  });
}

export function useNhumeMissions() {
  return useQuery({
    queryKey: nhumeQueryKeys.missions(),
    queryFn: () => nhumeApi.listMissions(),
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}

export function useCreateMission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => nhumeApi.createMission(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: nhumeQueryKeys.missions() }),
  });
}

export function useCancelMission(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: { reason?: string }) => nhumeApi.cancelMission(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.missions() });
      qc.invalidateQueries({ queryKey: nhumeQueryKeys.mission(id) });
    },
  });
}
