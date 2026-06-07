/**
 * Citizen monitoring devices — TanStack Query over BFF `/internal/v1/mobile/citizen/monitoring/*`.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchMonitoringDevices,
  fetchMonitoringReadings,
  fetchMonitoringAlerts,
  pairMonitoringDevice,
  reviewMonitoringAlert,
  syncMonitoringDevice,
} from "@/lib/citizen-monitoring-api";

const qk = {
  devices: (patientId: string) => ["citizen-monitoring", "devices", patientId] as const,
  readings: (patientId: string, type?: string) =>
    ["citizen-monitoring", "readings", patientId, type ?? "all"] as const,
  alerts: (patientId: string) => ["citizen-monitoring", "alerts", patientId] as const,
};

export function useMonitoringDevices(patientId: string | undefined) {
  return useQuery({
    queryKey: qk.devices(patientId ?? ""),
    queryFn: () => fetchMonitoringDevices(patientId!),
    enabled: !!patientId,
  });
}

export function useMonitoringReadings(patientId: string | undefined, type?: string) {
  return useQuery({
    queryKey: qk.readings(patientId ?? "", type),
    queryFn: () => fetchMonitoringReadings(patientId!, type),
    enabled: !!patientId,
  });
}

export function useMonitoringAlerts(patientId: string | undefined) {
  return useQuery({
    queryKey: qk.alerts(patientId ?? ""),
    queryFn: () => fetchMonitoringAlerts(patientId!),
    enabled: !!patientId,
  });
}

export function useReviewMonitoringAlert(patientId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { alertId: string; reviewNotes?: string }) =>
      reviewMonitoringAlert(args.alertId, { status: "REVIEWED", reviewNotes: args.reviewNotes }),
    onSuccess: async () => {
      if (patientId) {
        await qc.invalidateQueries({ queryKey: qk.alerts(patientId) });
      }
    },
  });
}

export function usePairMonitoringDevice(patientId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { deviceName: string; deviceType: string; manufacturer?: string; model?: string }) => {
      if (!patientId) throw new Error("patientId required");
      return pairMonitoringDevice({
        patientId,
        deviceName: args.deviceName,
        deviceType: args.deviceType,
        manufacturer: args.manufacturer,
        model: args.model,
      });
    },
    onSuccess: async () => {
      if (patientId) {
        await qc.invalidateQueries({ queryKey: qk.devices(patientId) });
      }
    },
  });
}

export function useSyncMonitoringDevice(patientId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: {
      deviceId: string;
      readings?: Array<{ vitalType?: string; value: number; unit?: string; measuredAt?: string; notes?: string }>;
    }) => syncMonitoringDevice(args.deviceId, args.readings ? { readings: args.readings } : undefined),
    onSuccess: async () => {
      if (patientId) {
        await qc.invalidateQueries({ queryKey: qk.devices(patientId) });
        await qc.invalidateQueries({ queryKey: qk.readings(patientId) });
      }
    },
  });
}
