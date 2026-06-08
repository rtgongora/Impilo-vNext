/**
 * Prescription Service — Rx creation and management.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/prescriptions/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import { authStore } from "@impilo/mobile-auth";
import type { Prescription, PrescriptionStatus } from "../types";
import { appStore } from "../stores/appStore";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

type Row = Record<string, unknown>;

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}

function num(v: unknown, fallback = 0): number {
  if (typeof v === "number" && !Number.isNaN(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    return Number.isNaN(n) ? fallback : n;
  }
  return fallback;
}

function prescriberId(): string {
  const session = authStore.getState().session;
  return session?.providerId ?? session?.actorId ?? "mobile-provider";
}

function facilityId(): string | undefined {
  return appStore.getState().facilityId ?? authStore.getState().session?.facilityId ?? undefined;
}

/** Exported for unit tests */
export function normalizePrescriptionResource(raw: unknown): Prescription | null {
  if (!raw || typeof raw !== "object") return null;
  const row = raw as Row;
  const attrs = (row.attributes as Row | undefined) ?? row;
  const id = str(row.id ?? attrs.id);
  if (!id) return null;

  return {
    id,
    encounterId: str(attrs.encounter_id ?? attrs.encounterId),
    patientId: str(attrs.patient_id ?? attrs.patientId),
    medication: str(attrs.medication_name ?? attrs.medication),
    dosage: str(attrs.dosage),
    frequency: str(attrs.frequency),
    duration: str(attrs.duration),
    quantity: num(attrs.quantity),
    instructions: attrs.instructions != null ? str(attrs.instructions) : undefined,
    status: str(attrs.status, "ACTIVE") as PrescriptionStatus,
    prescribedAt: str(attrs.created_at ?? attrs.prescribed_at ?? attrs.prescribedAt, new Date().toISOString()),
    prescribedBy: str(attrs.prescribed_by ?? attrs.prescribedBy, prescriberId()),
  };
}

export function normalizePrescriptionList(payload: unknown): Prescription[] {
  if (Array.isArray(payload)) {
    return payload.map(normalizePrescriptionResource).filter((rx): rx is Prescription => rx != null);
  }
  if (payload && typeof payload === "object") {
    const row = payload as Row;
    if (Array.isArray(row.data)) {
      return normalizePrescriptionList(row.data);
    }
    const single = normalizePrescriptionResource(row.data ?? row);
    return single ? [single] : [];
  }
  return [];
}

export interface CreatePrescriptionInput {
  encounterId: string;
  patientId: string;
  medication: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  instructions?: string;
  route?: string;
  indication?: string;
}

export async function createPrescription(input: CreatePrescriptionInput): Promise<Prescription> {
  const payload = {
    patient_id: input.patientId,
    encounter_id: input.encounterId,
    facility_id: facilityId(),
    medication_name: input.medication,
    dosage: input.dosage,
    route: input.route ?? "PO",
    frequency: input.frequency,
    duration: input.duration,
    quantity: input.quantity,
    instructions: input.instructions,
    indication: input.indication,
    prescribed_by: prescriberId(),
  };
  try {
    const response = await apiClient.post<{ data: unknown }>(
      "/internal/v1/mobile/provider/prescriptions",
      payload
    );
    const rx = normalizePrescriptionResource(response.data.data ?? response.data);
    if (!rx) throw new Error("Invalid prescription response");
    return rx;
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "clinical_prescriptions",
      path: "/internal/v1/mobile/provider/prescriptions",
      payload,
    });
    if (!offline) throw error;
    return {
      id: offline.recordId,
      encounterId: input.encounterId,
      patientId: input.patientId,
      medication: input.medication,
      dosage: input.dosage,
      frequency: input.frequency,
      duration: input.duration,
      quantity: input.quantity,
      instructions: input.instructions,
      status: "ACTIVE" as PrescriptionStatus,
      prescribedAt: new Date().toISOString(),
      prescribedBy: prescriberId(),
    };
  }
}

export async function getPrescriptionsForEncounter(encounterId: string, patientId?: string): Promise<Prescription[]> {
  if (!patientId) return [];
  const all = await getPrescriptionsForPatient(patientId);
  return all.filter((rx) => rx.encounterId === encounterId);
}

export async function getPrescriptionsForPatient(patientId: string): Promise<Prescription[]> {
  const response = await apiClient.get<{ data: unknown }>(
    `/internal/v1/mobile/provider/prescriptions?patient_id=${encodeURIComponent(patientId)}`
  );
  return normalizePrescriptionList(response.data.data ?? response.data);
}

export async function cancelPrescription(id: string): Promise<Prescription> {
  const response = await apiClient.post<{ data: unknown }>(
    `/internal/v1/mobile/provider/prescriptions/${id}/cancel`
  );
  const rx = normalizePrescriptionResource(response.data.data ?? response.data);
  if (!rx) throw new Error("Invalid cancel response");
  return rx;
}
