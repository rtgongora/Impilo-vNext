/**
 * Device Registry Hooks — Health OS §8 (Device/IoT Rails), §17 (Device Doctrine)
 *
 * "Devices are first-class entities in the Health Operating System and may
 *  participate directly in the experience layer."
 *
 * Queries the Experience BFF DeviceRegistryController, which manages device
 * registration, trust levels, and lifecycle. Telemetry ingestion is handled
 * separately by iot-ingestion-service.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type DeviceTrustLevel = "UNVERIFIED" | "BASIC" | "CERTIFIED" | "MEDICAL_GRADE";

export interface RegisteredDevice {
  deviceId: string;
  externalDeviceId: string;
  deviceType: string;
  manufacturer?: string;
  model?: string;
  firmwareVersion?: string;
  trustLevel: DeviceTrustLevel;
  status: "ACTIVE" | "REVOKED" | "PENDING_VERIFICATION";
  ownerHealthId?: string;
  lastSeenAt?: string;
  capabilities: string[];
}

export interface DeviceTrustAssessment {
  deviceId: string;
  trustLevel: DeviceTrustLevel;
  trustScore: number;
  factors: Array<{ name: string; status: string; weight: number }>;
  permittedOperations: string[];
  restrictedOperations: string[];
  upgradeActions: string[];
}

/** List the current user's registered devices. */
export function useMyDevices() {
  return useQuery({
    queryKey: ["devices", "mine"],
    queryFn: () => apiClient.get<ApiResponse<RegisteredDevice[]>>("/internal/v1/devices"),
  });
}

/** Get trust assessment for a specific device. */
export function useDeviceTrust(deviceId: string | undefined) {
  return useQuery({
    queryKey: ["devices", "trust", deviceId],
    queryFn: () => apiClient.get<ApiResponse<DeviceTrustAssessment>>(`/internal/v1/devices/${deviceId}/trust`),
    enabled: !!deviceId,
  });
}

/** Register a new device. */
export function useRegisterDevice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      externalDeviceId: string;
      deviceType: string;
      manufacturer?: string;
      model?: string;
      capabilities?: string[];
    }) => apiClient.post<ApiResponse<RegisteredDevice>>("/internal/v1/devices/register", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["devices"] });
    },
  });
}

/** Revoke a device (remove trust and delink). */
export function useRevokeDevice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { deviceId: string; reason: string }) =>
      apiClient.post<ApiResponse<{ status: string }>>(`/internal/v1/devices/${body.deviceId}/revoke`, { reason: body.reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["devices"] });
    },
  });
}
