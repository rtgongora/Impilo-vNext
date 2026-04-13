/**
 * Experience UI -- Bed Management Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface BedResource {
  id: string;
  type: "bed";
  attributes: {
    bedNumber: string;
    wardId: string;
    wardName: string;
    facilityId: string;
    status: string;
    patientId: string | null;
    patientName: string | null;
    patientMrn: string | null;
    patientDiagnosis: string | null;
    patientAttendingPhysician: string | null;
    patientAcuityLevel: string | null;
    patientAdmissionDate: string | null;
    patientAge: number | null;
    patientGender: string | null;
    [key: string]: unknown;
  };
}

export interface WardResource {
  id: string;
  type: "ward";
  attributes: {
    name: string;
    facilityId: string;
    totalBeds: number;
    occupiedBeds: number;
    availableBeds: number;
    maintenanceBeds: number;
    [key: string]: unknown;
  };
}

export function useBeds(facilityId: string) {
  return useQuery<ApiResponse<BedResource[]>>({
    queryKey: ["beds", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<BedResource[]>>(
        `/internal/v1/beds?facility_id=${encodeURIComponent(facilityId)}`
      ),
    enabled: !!facilityId,
  });
}

export function useWards(facilityId: string) {
  return useQuery<ApiResponse<WardResource[]>>({
    queryKey: ["wards", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<WardResource[]>>(
        `/internal/v1/beds/wards?facility_id=${encodeURIComponent(facilityId)}`
      ),
    enabled: !!facilityId,
  });
}

export function useAssignBed() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ bedId, patientId }: { bedId: string; patientId: string }) =>
      apiClient.post(`/internal/v1/beds/${bedId}/assign`, { patientId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["beds"] });
      qc.invalidateQueries({ queryKey: ["wards"] });
    },
  });
}

export function useDischargeBed() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (bedId: string) =>
      apiClient.post(`/internal/v1/beds/${bedId}/discharge`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["beds"] });
      qc.invalidateQueries({ queryKey: ["wards"] });
    },
  });
}

export function useChangeBedStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ bedId, status }: { bedId: string; status: string }) =>
      apiClient.post(`/internal/v1/beds/${bedId}/status`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["beds"] });
      qc.invalidateQueries({ queryKey: ["wards"] });
    },
  });
}
