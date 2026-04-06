"use client";

/**
 * useWardData — Hook for ward/bed data fetching with mock data.
 *
 * In production this will fetch from the VARAPI service via apiClient.
 * Currently returns deterministic mock data for all wards and beds.
 */

import { useState, useCallback, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// --- Types ---

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

// --- Constants ---

export const WARDS: Ward[] = [
  { id: "ward-med", name: "Medical Ward", type: "medical" },
  { id: "ward-surg", name: "Surgical Ward", type: "surgical" },
  { id: "ward-icu", name: "ICU", type: "icu" },
  { id: "ward-peds", name: "Pediatrics", type: "pediatrics" },
  { id: "ward-mat", name: "Maternity", type: "maternity" },
  { id: "ward-emg", name: "Emergency", type: "emergency" },
];

// --- Mock Data Generator ---

function generateMockBeds(): BedData[] {
  const beds: BedData[] = [];
  const patients: Array<Omit<PatientOnBed, "id"> & { id: string }> = [
    { id: "p-001", name: "M. Ndlovu", mrn: "MRN-10234", diagnosis: "Pneumonia", attendingPhysician: "Dr. Nkomo", acuityLevel: "high", admissionDate: new Date("2026-04-02") },
    { id: "p-002", name: "S. Khumalo", mrn: "MRN-10456", diagnosis: "Sepsis", attendingPhysician: "Dr. Sibanda", acuityLevel: "critical", admissionDate: new Date("2026-04-01") },
    { id: "p-003", name: "T. Dlamini", mrn: "MRN-10789", diagnosis: "Post-op hip replacement", attendingPhysician: "Dr. Mhlanga", acuityLevel: "medium", admissionDate: new Date("2026-04-04") },
    { id: "p-004", name: "L. Phiri", mrn: "MRN-11002", diagnosis: "Acute appendicitis", attendingPhysician: "Dr. Nkomo", acuityLevel: "high", admissionDate: new Date("2026-04-05") },
    { id: "p-005", name: "J. Moyo", mrn: "MRN-11045", diagnosis: "COPD exacerbation", attendingPhysician: "Dr. Zulu", acuityLevel: "medium", admissionDate: new Date("2026-04-03") },
    { id: "p-006", name: "R. Banda", mrn: "MRN-11078", diagnosis: "Diabetic ketoacidosis", attendingPhysician: "Dr. Sibanda", acuityLevel: "critical", admissionDate: new Date("2026-04-04") },
    { id: "p-007", name: "A. Ncube", mrn: "MRN-11090", diagnosis: "Fracture femur", attendingPhysician: "Dr. Mhlanga", acuityLevel: "medium", admissionDate: new Date("2026-04-05") },
    { id: "p-008", name: "N. Tshabalala", mrn: "MRN-11105", diagnosis: "Preeclampsia", attendingPhysician: "Dr. Chikwanda", acuityLevel: "high", admissionDate: new Date("2026-04-03") },
    { id: "p-009", name: "G. Maposa", mrn: "MRN-11120", diagnosis: "Asthma acute", attendingPhysician: "Dr. Nkomo", acuityLevel: "low", admissionDate: new Date("2026-04-06") },
    { id: "p-010", name: "K. Mthembu", mrn: "MRN-11133", diagnosis: "Malaria severe", attendingPhysician: "Dr. Zulu", acuityLevel: "high", admissionDate: new Date("2026-04-02") },
    { id: "p-011", name: "B. Sithole", mrn: "MRN-11150", diagnosis: "Neonatal jaundice", attendingPhysician: "Dr. Mhlanga", acuityLevel: "medium", admissionDate: new Date("2026-04-05") },
    { id: "p-012", name: "C. Ngwenya", mrn: "MRN-11162", diagnosis: "Chest trauma", attendingPhysician: "Dr. Nkomo", acuityLevel: "critical", admissionDate: new Date("2026-04-06") },
    { id: "p-013", name: "D. Chirwa", mrn: "MRN-11178", diagnosis: "Eclampsia", attendingPhysician: "Dr. Chikwanda", acuityLevel: "critical", admissionDate: new Date("2026-04-04") },
    { id: "p-014", name: "E. Dube", mrn: "MRN-11190", diagnosis: "Burn injury", attendingPhysician: "Dr. Sibanda", acuityLevel: "high", admissionDate: new Date("2026-04-03") },
  ];

  const wardBedCounts: Record<string, number> = {
    "ward-med": 28,
    "ward-surg": 22,
    "ward-icu": 10,
    "ward-peds": 20,
    "ward-mat": 20,
    "ward-emg": 20,
  };

  // Deterministic occupancy per ward
  const wardOccupancy: Record<string, number> = {
    "ward-med": 24,
    "ward-surg": 18,
    "ward-icu": 9,
    "ward-peds": 14,
    "ward-mat": 16,
    "ward-emg": 17,
  };

  let patientIdx = 0;

  for (const ward of WARDS) {
    const count = wardBedCounts[ward.id] || 10;
    const occupied = wardOccupancy[ward.id] || 0;

    for (let i = 1; i <= count; i++) {
      const bedNumber = `${i}${String.fromCharCode(65 + (i % 3))}`;
      let status: BedStatus = "available";
      let patient: PatientOnBed | undefined;

      if (i <= occupied) {
        status = "occupied";
        const p = patients[patientIdx % patients.length];
        patient = { ...p };
        patientIdx++;
      } else if (i === occupied + 1 && count > occupied + 2) {
        status = "maintenance";
      } else if (i === occupied + 2 && count > occupied + 3) {
        status = "cleaning";
      }

      beds.push({
        id: `${ward.id}-bed-${i}`,
        bedNumber,
        wardId: ward.id,
        wardName: ward.name,
        status,
        patient,
      });
    }
  }

  return beds;
}

// --- Hook ---

export function useWardData() {
  const queryClient = useQueryClient();

  const { data: beds = [], isLoading: loading } = useQuery({
    queryKey: ["ward-beds"],
    queryFn: async () => {
      // Simulate network delay
      await new Promise((r) => setTimeout(r, 300));
      return generateMockBeds();
    },
    staleTime: 30_000,
  });

  const refetch = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ["ward-beds"] });
  }, [queryClient]);

  const updateBedStatus = useCallback(
    async (
      bedId: string,
      action: "admit" | "discharge" | "maintenance" | "clean",
      patientData?: Partial<PatientOnBed>
    ) => {
      // In production, this would call the VARAPI endpoint
      queryClient.setQueryData<BedData[]>(["ward-beds"], (prev) => {
        if (!prev) return prev;
        return prev.map((bed) => {
          if (bed.id !== bedId) return bed;
          switch (action) {
            case "admit":
              return {
                ...bed,
                status: "occupied" as BedStatus,
                patient: {
                  id: patientData?.id || `p-${Date.now()}`,
                  name: patientData?.name || "Unknown",
                  mrn: patientData?.mrn || "MRN-00000",
                  diagnosis: patientData?.diagnosis || "",
                  attendingPhysician: patientData?.attendingPhysician || "",
                  acuityLevel: patientData?.acuityLevel || "medium",
                  admissionDate: patientData?.admissionDate || new Date(),
                },
              };
            case "discharge":
              return { ...bed, status: "cleaning" as BedStatus, patient: undefined };
            case "maintenance":
              return { ...bed, status: "maintenance" as BedStatus };
            case "clean":
              return { ...bed, status: "available" as BedStatus };
            default:
              return bed;
          }
        });
      });
    },
    [queryClient]
  );

  return { beds, loading, refetch, updateBedStatus };
}
