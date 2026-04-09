"use client";

/**
 * useWardData — Ward/bed data from Experience BFF (`/internal/v1/beds/*`) for the selected facility.
 */

import { useCallback, useMemo } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  facilityOpsKeys,
  type BedResource,
  useFacilityBeds,
} from "@/hooks/queries/useFacilityOperations";
import { apiClient } from "@/lib/api-client";

export type BedStatus = "occupied" | "available" | "maintenance" | "cleaning";

export type AcuityLevel = "critical" | "high" | "medium" | "low";

export interface PatientOnBed {
  id: string;
  name: string;
  mrn: string;
  diagnosis: string;
  attendingPhysician: string;
  acuityLevel: AcuityLevel;
  admissionDate: Date;
}

export interface BedData {
  id: string;
  bedNumber: string;
  wardId: string;
  wardName: string;
  status: BedStatus;
  patient?: PatientOnBed;
}

export interface Ward {
  id: string;
  name: string;
  type: "medical" | "surgical" | "icu" | "pediatrics" | "maternity" | "emergency";
}

function mapApiBedStatus(status: string): BedStatus {
  switch (status.toUpperCase()) {
    case "OCCUPIED":
      return "occupied";
    case "MAINTENANCE":
      return "maintenance";
    case "CLEANING":
      return "cleaning";
    case "RESERVED":
    case "AVAILABLE":
    default:
      return "available";
  }
}

function mapAcuity(a: string | null | undefined): AcuityLevel {
  const u = (a ?? "").toUpperCase();
  if (u === "CRITICAL") return "critical";
  if (u === "HIGH") return "high";
  if (u === "LOW") return "low";
  return "medium";
}

function mapBedResource(row: BedResource): BedData {
  const a = row.attributes;
  const status = mapApiBedStatus(a.status);
  let patient: PatientOnBed | undefined;
  if (status === "occupied" && a.patientId) {
    const admitted = a.admittedAt ? new Date(a.admittedAt) : new Date();
    patient = {
      id: a.patientId,
      name: a.patientName ?? "Patient",
      mrn: "",
      diagnosis: "",
      attendingPhysician: a.assignedDoctor ?? "",
      acuityLevel: mapAcuity(a.acuity),
      admissionDate: admitted,
    };
  }
  return {
    id: row.id,
    bedNumber: String(a.bedNumber),
    wardId: a.wardId,
    wardName: a.wardName,
    status,
    patient,
  };
}

export function useWardData() {
  const facilityId = useFacilityStore((s) => s.facility?.id);
  const queryClient = useQueryClient();
  const { data: bedsResponse, isLoading: loading } = useFacilityBeds(facilityId);

  const beds: BedData[] = useMemo(
    () => (bedsResponse?.data ?? []).map(mapBedResource),
    [bedsResponse]
  );

  const refetch = useCallback(() => {
    if (facilityId) {
      void queryClient.invalidateQueries({ queryKey: facilityOpsKeys.beds(facilityId) });
    }
  }, [queryClient, facilityId]);

  const updateBedStatus = useCallback(
    async (
      bedId: string,
      action: "admit" | "discharge" | "maintenance" | "clean",
      patientData?: Partial<PatientOnBed>
    ) => {
      if (!facilityId) return;
      if (action === "discharge") {
        await apiClient.post(`/internal/v1/beds/${bedId}/discharge`, {});
      } else if (action === "maintenance") {
        await apiClient.post(`/internal/v1/beds/${bedId}/status`, { status: "MAINTENANCE" });
      } else if (action === "clean") {
        await apiClient.post(`/internal/v1/beds/${bedId}/status`, { status: "AVAILABLE" });
      } else if (action === "admit" && patientData?.id) {
        await apiClient.post(`/internal/v1/beds/${bedId}/assign`, {
          patientId: patientData.id,
          patientName: patientData.name ?? "Patient",
          acuity: (patientData.acuityLevel ?? "medium").toUpperCase(),
          assignedDoctor: patientData.attendingPhysician ?? "",
        });
      }
      void queryClient.invalidateQueries({ queryKey: facilityOpsKeys.all(facilityId) });
    },
    [facilityId, queryClient]
  );

  return { beds, loading, refetch, updateBedStatus };
}
