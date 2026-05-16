import { apiClient } from "@impilo/mobile-api-client";
import type { Allergy, Condition, Provider } from "../types";
import { discoverServices } from "./serviceDiscoveryService";

type AnyRecord = Record<string, unknown>;

function asRecord(value: unknown): AnyRecord | null {
  return typeof value === "object" && value !== null ? (value as AnyRecord) : null;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function fallbackId(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

function toCondition(raw: unknown): Condition {
  const item = asRecord(raw) ?? {};
  return {
    id: String(item.id ?? item.conditionId ?? fallbackId("cond")),
    name: String(item.display ?? item.name ?? item.code ?? "Condition"),
    code: item.code ? String(item.code) : undefined,
    status: String(item.clinicalStatus ?? item.status ?? "ACTIVE") as Condition["status"],
    clinicalStatus: String(item.clinicalStatus ?? item.status ?? "ACTIVE") as Condition["status"],
    onsetDate: item.onsetDate ? String(item.onsetDate) : undefined,
    recordedAt: String(item.recordedAt ?? item.onsetDate ?? new Date().toISOString()),
    notes: item.notes ? String(item.notes) : undefined,
  };
}

function toAllergy(raw: unknown): Allergy {
  const item = asRecord(raw) ?? {};
  const severity = String(item.criticality ?? item.severity ?? "MODERATE");
  const normalizedSeverity: Allergy["severity"] =
    severity === "MILD" || severity === "SEVERE" || severity === "LIFE_THREATENING"
      ? severity
      : "MODERATE";

  return {
    id: String(item.id ?? item.allergyId ?? fallbackId("alg")),
    allergen: String(item.substance ?? item.allergen ?? "Allergen"),
    reaction: String(item.reaction ?? "Not specified"),
    severity: normalizedSeverity,
    recordedAt: String(item.recordedAt ?? new Date().toISOString()),
    onsetDate: item.onsetDate ? String(item.onsetDate) : undefined,
    notes: item.notes ? String(item.notes) : undefined,
  };
}

export async function fetchConditions(status?: "ALL" | "ACTIVE" | "RESOLVED"): Promise<Condition[]> {
  const response = await apiClient.get<{ data?: { conditions?: unknown[] } }>(
    "/internal/v1/mobile/citizen/summary"
  );
  const data = asRecord(response.data?.data);
  const conditions = asArray(data?.conditions).map(toCondition);
  if (!status || status === "ALL") {
    return conditions;
  }
  return conditions.filter((condition) => condition.status === status);
}

export async function fetchAllergies(): Promise<Allergy[]> {
  const response = await apiClient.get<{ data?: { allergies?: unknown[] } }>(
    "/internal/v1/mobile/citizen/summary"
  );
  const data = asRecord(response.data?.data);
  return asArray(data?.allergies).map(toAllergy);
}

export async function discoverProviders(query?: string, specialty?: string): Promise<Provider[]> {
  const services = await discoverServices(query, specialty);
  return services.map((service, index) => ({
    id: service.id || `provider-${index}`,
    displayName: service.name,
    title: specialty ?? "PROVIDER",
    specialty: service.category,
    facilityName: service.facilityName,
    reviewCount: 0,
    isAccepting: service.available,
    consultationFee: service.price,
    currency: service.currency,
    rating: service.rating,
  }));
}
