import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface BffEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface ControlTowerAggregate {
  facilityCount: number;
  openAlertCount: number;
  visibility: "ROW_DETAIL" | "AGGREGATE_ONLY";
  facilities: Array<{
    facilityId: number;
    name: string;
    openAlerts: number;
    bedOccupancyPercent: number | null;
  }>;
}

export interface FacilitySummary {
  facilityId: number;
  facilityName: string;
  facilityType: string;
  status: string;
  occupancy: {
    totalBeds: number;
    occupiedBeds: number;
    availableBeds: number;
    snapshotTime: string | null;
  };
  recentTelemetry: Array<{
    source: string;
    metricType: string;
    metricValue: number;
    unit: string | null;
    recordedAt: string;
  }>;
  activeAlertCount: number;
  workspaceCount: number;
  activeShiftCount: number;
}

export interface Alert {
  id: string;
  facilityId: number;
  facilityName: string;
  alertType: string;
  severity: string;
  title: string;
  description: string | null;
  status: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string;
}

export function useControlTowerAggregate() {
  return useQuery({
    queryKey: ["control-tower-aggregate"],
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<ControlTowerAggregate>>(
        `/internal/v1/facility-mode/control-tower/aggregate`,
      );
      return resp.data;
    },
  });
}

export function useFacilitySummary(facilityId: string | number) {
  return useQuery({
    queryKey: ["control-tower-summary", String(facilityId)],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<FacilitySummary>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/control-tower/summary`,
      );
      return resp.data;
    },
  });
}

export function useFacilityAlerts(facilityId: string | number, status = "OPEN") {
  return useQuery({
    queryKey: ["control-tower-alerts", String(facilityId), status],
    enabled: facilityId != null && facilityId !== "",
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<Alert[]>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/control-tower/alerts?status=${encodeURIComponent(status)}`,
      );
      return resp.data;
    },
  });
}

export function useAcknowledgeAlert(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (alertId: string) => {
      const resp = await apiClient.post<BffEnvelope<Alert>>(
        `/internal/v1/facility-mode/control-tower/alerts/${encodeURIComponent(alertId)}/acknowledge`,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["control-tower-alerts", String(facilityId)] });
      qc.invalidateQueries({ queryKey: ["control-tower-summary", String(facilityId)] });
    },
  });
}
