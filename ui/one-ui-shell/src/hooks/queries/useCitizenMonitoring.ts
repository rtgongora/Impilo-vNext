/**
 * Citizen monitoring devices — TanStack Query over BFF `/internal/v1/mobile/citizen/monitoring/*`.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchMonitoringDevices,
  fetchMonitoringReadings,
  pairMonitoringDevice,
  syncMonitoringDevice,
} from "@/lib/citizen-monitoring-api";

const qk = {
  devices: (patientId: string) => ["citizen-monitoring", "devices", patientId] as const,
  readings: (patientId: string, type?: string) =>
    ["citizen-monitoring", "readings", patientId, type ?? "all"] as const,
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
    mutationFn: (deviceId: string) => syncMonitoringDevice(deviceId),
    onSuccess: async () => {
      if (patientId) {
        await qc.invalidateQueries({ queryKey: qk.devices(patientId) });
      }
    },
  });
}
