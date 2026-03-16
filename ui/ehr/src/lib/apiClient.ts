import type { Patient, Encounter, VitalSign, Prescription } from "@/stores/ehrStore";

const BFF_URL = process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8100";

interface LabResult {
  testName: string;
  value: string;
  unit: string;
  date: string;
  abnormal: boolean;
}

async function fetchJson<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BFF_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-ID": sessionStorage.getItem("tenantId") || "default",
      "X-Correlation-ID": crypto.randomUUID(),
      ...(options?.headers || {}),
    },
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

export const apiClient = {
  searchPatients: (query: string): Promise<Patient[]> =>
    fetchJson(`/internal/v1/patients/search?q=${encodeURIComponent(query)}`),

  getEncounters: (cpid: string): Promise<Encounter[]> =>
    fetchJson(`/internal/v1/patients/${cpid}/encounters`),

  getLatestVitals: (cpid: string): Promise<VitalSign[]> =>
    fetchJson(`/internal/v1/patients/${cpid}/vitals/latest`),

  getActiveMedications: (cpid: string): Promise<Prescription[]> =>
    fetchJson(`/internal/v1/patients/${cpid}/medications?status=ACTIVE`),

  getLabResults: (cpid: string): Promise<LabResult[]> =>
    fetchJson(`/internal/v1/patients/${cpid}/lab-results?limit=10`),

  createEncounter: (cpid: string, data: Partial<Encounter>): Promise<Encounter> =>
    fetchJson(`/internal/v1/patients/${cpid}/encounters`, {
      method: "POST",
      body: JSON.stringify(data),
    }),

  addVitals: (encounterId: string, vitals: VitalSign[]): Promise<void> =>
    fetchJson(`/internal/v1/encounters/${encounterId}/vitals`, {
      method: "POST",
      body: JSON.stringify(vitals),
    }),

  addPrescription: (encounterId: string, rx: Prescription): Promise<Prescription> =>
    fetchJson(`/internal/v1/encounters/${encounterId}/prescriptions`, {
      method: "POST",
      body: JSON.stringify(rx),
    }),
};
