import type { Patient, Encounter, VitalSign, Prescription } from "@/stores/ehrStore";

const BFF_URL = process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8100";

export interface LabResult {
  testName: string;
  value: string;
  unit: string;
  date: string;
  abnormal: boolean;
}

export interface DashboardSummary {
  activeJourneys: number;
  pendingOrders: number;
  pendingResults: number;
  todayEncounters: number;
  criticalAlerts: number;
}

export interface Journey {
  id: string;
  patientCpid: string;
  state:
    | "REGISTERED"
    | "TRIAGED"
    | "WAITING"
    | "IN_CONSULTATION"
    | "REFERRED"
    | "LAB_ORDERED"
    | "PHARMACY"
    | "IN_PROCEDURE"
    | "OBSERVATION"
    | "DECISION"
    | "DISCHARGE_PENDING"
    | "DISCHARGED"
    | "CANCELLED";
  facilityId: string;
  startedAt: string;
  updatedAt: string;
  chiefComplaint?: string;
}

export interface ClinicalOrder {
  id: string;
  patientCpid: string;
  orderType: "LAB" | "IMAGING" | "PROCEDURE" | "REFERRAL" | "MEDICATION";
  status:
    | "PLACED"
    | "ACKNOWLEDGED"
    | "IN_PROGRESS"
    | "FULFILLED"
    | "CANCELLED"
    | "SLA_BREACHED";
  priority: "ROUTINE" | "URGENT" | "STAT";
  description: string;
  orderedBy: string;
  orderedAt: string;
  dueBy?: string;
}

export interface ClinicalResult {
  id: string;
  orderId: string;
  patientCpid: string;
  resultType: "LAB" | "IMAGING" | "PROCEDURE";
  status: "PRELIMINARY" | "FINAL" | "AMENDED" | "CANCELLED";
  summary: string;
  abnormal: boolean;
  critical: boolean;
  reportedAt: string;
  signedOffBy?: string;
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

  getDashboardSummary: (): Promise<DashboardSummary> =>
    fetchJson(`/internal/v1/dashboard/summary`),

  getPatient: (cpid: string): Promise<Patient> =>
    fetchJson(`/internal/v1/patients/${cpid}`),

  getJourneyTimeline: (cpid: string): Promise<Journey[]> =>
    fetchJson(`/internal/v1/patients/${cpid}/journeys`),

  listEncounters: (status?: string): Promise<Encounter[]> =>
    fetchJson(
      `/internal/v1/encounters${status ? `?status=${encodeURIComponent(status)}` : ""}`
    ),

  listOrders: (status?: string, priority?: string): Promise<ClinicalOrder[]> =>
    fetchJson(
      `/internal/v1/orders?${status ? `status=${encodeURIComponent(status)}&` : ""}${priority ? `priority=${encodeURIComponent(priority)}` : ""}`
    ),

  listResults: (status?: string): Promise<ClinicalResult[]> =>
    fetchJson(
      `/internal/v1/results${status ? `?status=${encodeURIComponent(status)}` : ""}`
    ),
};
