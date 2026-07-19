import type { Patient, Encounter, VitalSign, Prescription } from "@/stores/ehrStore";

const BFF_URL = process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160";

interface LabResult {
  testName: string;
  value: string;
  unit: string;
  date: string;
  abnormal: boolean;
}

function trustHeaders(): Record<string, string> {
  if (typeof window === "undefined" || typeof sessionStorage === "undefined") {
    return {};
  }
  return {
    "X-Tenant-ID": sessionStorage.getItem("tenantId") || "default",
    "X-Pod-ID": sessionStorage.getItem("podId") || "default-pod",
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": crypto.randomUUID(),
    "X-Facility-ID": sessionStorage.getItem("facilityId") || "",
    "X-Purpose-Of-Use": sessionStorage.getItem("purposeOfUse") || "TREATMENT",
    "X-Access-Mode": "INTERNAL",
  };
}

export async function fetchJson<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BFF_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...trustHeaders(),
      ...(options?.headers || {}),
    },
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

function mapGender(s: string | undefined): Patient["gender"] {
  const g = (s ?? "").toLowerCase();
  if (g === "female") return "FEMALE";
  if (g === "male") return "MALE";
  return "OTHER";
}

/** Map BFF patient resource → EHR Patient (CPID anchor; PII from Vito). */
export function patientResourceToPatient(resource: {
  id: string;
  attributes: Record<string, unknown>;
}): Patient {
  const a = resource.attributes;
  const given = String(a.givenName ?? "").trim();
  const family = String(a.familyName ?? "").trim();
  const display = String(a.displayName ?? `${given} ${family}`).trim();
  const parts = display.split(/\s+/);
  const firstName = given || parts[0] || "Unknown";
  const lastName = family || (parts.length > 1 ? parts.slice(1).join(" ") : "Unknown");
  return {
    // D7: never treat a raw Health ID as the clinical key — cpid or the
    // resource's own id only.
    cpid: String(a.cpid ?? resource.id),
    firstName,
    lastName,
    dateOfBirth: String(a.dateOfBirth ?? ""),
    gender: mapGender(String(a.sex ?? a.gender)),
    nationalId: a.nationalId ? String(a.nationalId) : undefined,
    facilityId: a.facilityId ? String(a.facilityId) : undefined,
    allergies: [],
    conditions: [],
  };
}

function unwrapSearchPayload(data: unknown): unknown[] {
  if (!data || typeof data !== "object") return [];
  const d = data as Record<string, unknown>;
  if (Array.isArray(d.candidates)) return d.candidates;
  if (Array.isArray(d.items)) return d.items;
  if (Array.isArray(d.content)) return d.content;
  if (Array.isArray(d)) return d;
  if (d.data && typeof d.data === "object") {
    return unwrapSearchPayload(d.data);
  }
  return [];
}

export const apiClient = {
  searchPatients: async (query: string): Promise<Patient[]> => {
    if (query.length < 2) return [];
    const res = await fetchJson<{ data: unknown }>(`/internal/v1/patients/search`, {
      method: "POST",
      body: JSON.stringify({ query }),
    });
    const rows = unwrapSearchPayload(res.data);
    return rows
      .map((row) => {
        if (!row || typeof row !== "object") return null;
        const r = row as Record<string, unknown>;
        const id = String(r.cpid ?? r.id ?? crypto.randomUUID());
        return patientResourceToPatient({
          id,
          attributes: {
            ...r,
            givenName: r.givenName ?? r.firstName,
            familyName: r.familyName ?? r.lastName,
            displayName: r.displayName,
            dateOfBirth: r.dateOfBirth ?? r.dob,
            sex: r.sex ?? r.gender,
            cpid: r.cpid ?? r.impiloId ?? id,
          },
        });
      })
      .filter(Boolean) as Patient[];
  },

  registerPatient: (body: Record<string, unknown>) =>
    fetchJson<{ data: { id: string; attributes: Record<string, unknown> } }>(`/internal/v1/patients`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

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
