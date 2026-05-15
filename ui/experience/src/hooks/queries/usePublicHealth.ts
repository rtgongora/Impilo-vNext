import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" ? (value as UnknownRecord) : {};
}

/**
 * Unwraps BFF `data` payloads that mirror upstream services:
 * surveillance uses `items`, counters use `counters`, alerts use `alerts`,
 * indawo list uses `items`, some services wrap rows in `data`.
 */
export function extractPublicHealthList(payload: unknown, listKeys: readonly string[]): unknown[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload;
  const o = payload as Record<string, unknown>;
  for (const key of listKeys) {
    const v = o[key];
    if (Array.isArray(v)) return v;
  }
  if (Array.isArray(o.items)) return o.items as unknown[];
  if (Array.isArray(o.data)) return o.data as unknown[];
  return [];
}

function getAttributes(value: unknown): UnknownRecord {
  const record = asRecord(value);
  const attributes = record.attributes;
  return attributes && typeof attributes === "object" ? (attributes as UnknownRecord) : record;
}

function readString(record: UnknownRecord, ...keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.length > 0) return value;
  }
  return "";
}

function readNumber(record: UnknownRecord, ...keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim().length > 0) {
      const parsed = Number(value);
      if (!Number.isNaN(parsed)) return parsed;
    }
  }
  return 0;
}

function readBoolean(record: UnknownRecord, ...keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "boolean") return value;
  }
  return false;
}

export interface PublicHealthSignal {
  id: string;
  disease: string;
  facility: string;
  cases: number;
  threshold: number;
  status: string;
  detectedAt: string;
}

export interface PublicHealthCase {
  id: string;
  disease: string;
  patientRef: string;
  facility: string;
  status: string;
  outcome: string;
  reportedAt: string;
}

export interface PublicHealthAlert {
  id: string;
  title: string;
  severity: string;
  location: string;
  status: string;
  detectedAt: string;
}

export interface PublicHealthCounter {
  id: string;
  label: string;
  value: string;
  detail: string;
}

export interface PublicHealthCampaign {
  id: string;
  name: string;
  status: string;
  campaignType: string;
  jurisdiction: string;
  targetPopulation: number;
  reachedPopulation: number;
  startDate: string;
  endDate: string;
}

export interface PublicHealthSite {
  id: string;
  name: string;
  siteType: string;
  jurisdiction: string;
  operationalStatus: string;
}

export function normalizeSignal(resource: unknown): PublicHealthSignal {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id;
  const id = idVal != null ? String(idVal) : "";
  const name = readString(record, "name", "disease", "condition");
  const eventType = readString(record, "eventType", "event_type");
  return {
    id,
    disease: name || eventType || "Signal definition",
    facility:
      readString(record, "facility_name", "facility", "source_facility", "conditionField", "condition_field") ||
      "—",
    cases: readNumber(record, "case_count", "cases", "count"),
    threshold: readNumber(record, "threshold", "threshold_value"),
    status: readString(record, "status") || "ACTIVE",
    detectedAt: readString(record, "detected_at", "reported_at", "createdAt", "created_at"),
  };
}

export function normalizeCase(resource: unknown): PublicHealthCase {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id;
  const id = idVal != null ? String(idVal) : "";
  const facilityId = record.facilityId ?? record.facility_id;
  return {
    id,
    disease:
      readString(record, "title", "disease", "condition", "caseType", "case_type") || "Surveillance case",
    patientRef:
      readString(record, "patient_ref", "cpid", "client_id") ||
      (record.signalHitId != null || record.signal_hit_id != null
        ? `Hit ${record.signalHitId ?? record.signal_hit_id}`
        : "—"),
    facility: readString(record, "facility_name", "facility") || (facilityId != null ? String(facilityId) : "—"),
    status: readString(record, "status") || "OPEN",
    outcome: readString(record, "outcome", "description") || readString(record, "severity") || "—",
    reportedAt: readString(record, "reported_at", "createdAt", "created_at", "detected_at"),
  };
}

export function normalizeAlert(resource: unknown): PublicHealthAlert {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id;
  const syndrome = readString(record, "syndrome_code", "syndromeCode");
  const facility = record.facility_id ?? record.facilityId;
  return {
    id: idVal != null ? String(idVal) : "",
    title:
      readString(record, "title", "name", "alert_name") ||
      (syndrome ? `Syndrome ${syndrome}` : "Surveillance alert"),
    severity: readString(record, "severity", "priority") || "medium",
    location: readString(record, "location", "area", "jurisdiction") || (facility != null ? String(facility) : "—"),
    status: readBoolean(record, "acknowledged") ? "acknowledged" : "active",
    detectedAt: readString(record, "detected_at", "triggered_at", "created_at", "reported_at"),
  };
}

export function normalizeCounter(resource: unknown): PublicHealthCounter {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id:
      readString(outer, "id") ||
      readString(record, "id") ||
      readString(record, "metric_key", "label", "name") ||
      crypto.randomUUID(),
    label: readString(record, "label", "name", "metric_name") || "Counter",
    value:
      readString(record, "value", "metric_value") ||
      String(readNumber(record, "count", "total", "metric_total")),
    detail: readString(record, "detail", "description"),
  };
}

export function normalizeCampaign(resource: unknown): PublicHealthCampaign {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id;
  return {
    id: idVal != null ? String(idVal) : "",
    name: readString(record, "name", "campaign_name") || "Unnamed campaign",
    status: readString(record, "status") || "planning",
    campaignType: readString(record, "campaignType", "campaign_type", "type") || "General",
    jurisdiction: readString(record, "jurisdiction", "location") || "National",
    targetPopulation: readNumber(record, "target_population", "target", "target_count"),
    reachedPopulation: readNumber(record, "reached_population", "reached", "completed_count"),
    startDate: readString(record, "start_date", "starts_on"),
    endDate: readString(record, "end_date", "ends_on"),
  };
}

function normalizeSite(resource: unknown): PublicHealthSite {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const siteId = outer.siteId ?? outer.id ?? record.siteId ?? record.site_id ?? record.id;
  return {
    id: siteId != null ? String(siteId) : "",
    name: readString(record, "name", "site_name") || "Unknown site",
    siteType: readString(record, "site_type", "type", "category") || "General",
    jurisdiction: readString(record, "jurisdiction", "district", "province") || "Unknown jurisdiction",
    operationalStatus:
      readString(record, "operational_status", "status") ||
      (readBoolean(record, "active", "is_active") ? "ACTIVE" : "UNKNOWN"),
  };
}

export function usePublicHealthSignals() {
  return useQuery({
    queryKey: ["public-health-signals"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/signals");
      return extractPublicHealthList(response.data, ["items"]).map(normalizeSignal);
    },
  });
}

export function usePublicHealthCases() {
  return useQuery({
    queryKey: ["public-health-cases"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/cases");
      return extractPublicHealthList(response.data, ["items"]).map(normalizeCase);
    },
  });
}

export function usePublicHealthAlerts() {
  return useQuery({
    queryKey: ["public-health-alerts"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/alerts");
      return extractPublicHealthList(response.data, ["alerts"]).map(normalizeAlert);
    },
  });
}

/** Normalizes surveillance counter payloads (BFF GET /public-health/counters). */
export function parseCountersPayload(raw: unknown): PublicHealthCounter[] {
  const rows = extractPublicHealthList(raw, ["counters"]);
  if (rows.length > 0) {
    return rows.map((row) => {
      const r = asRecord(row);
      const label = readString(r, "syndrome_code", "label") || "Counter";
      const val = readNumber(r, "event_count", "count", "value");
      return normalizeCounter({
        id: readString(r, "facility_id", "id") + label + readString(r, "count_date"),
        label,
        value: String(val),
        detail: `${readString(r, "facility_id", "facility")} · ${readString(r, "count_date")}`,
      });
    });
  }
  if (Array.isArray(raw)) return raw.map(normalizeCounter);
  const record = asRecord(raw);
  return Object.entries(record).map(([key, value]) =>
    normalizeCounter({
      id: key,
      label: key.replace(/_/g, " "),
      value,
    }),
  );
}

export function usePublicHealthCounters() {
  return useQuery({
    queryKey: ["public-health-counters"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/counters");
      return parseCountersPayload(response.data);
    },
  });
}

export function usePublicHealthWeeklyIdsr() {
  return useQuery({
    queryKey: ["public-health-weekly-idsr"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/weekly-idsr");
      return parseCountersPayload(response.data);
    },
  });
}

export function usePublicHealthCampaigns() {
  return useQuery({
    queryKey: ["public-health-campaigns"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/campaigns");
      const d = asRecord(response.data);
      const rows = Array.isArray(d.content)
        ? (d.content as unknown[])
        : extractPublicHealthList(response.data, ["items"]);
      return rows.map(normalizeCampaign);
    },
  });
}

export function usePublicHealthSites() {
  return useQuery({
    queryKey: ["public-health-sites"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/sites");
      return extractPublicHealthList(response.data, ["items"]).map(normalizeSite);
    },
  });
}

export function useCreatePublicHealthCampaign() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/public-health/campaigns", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-campaigns"] });
    },
  });
}

export function useDispatchPublicHealthCampaign() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (campaignId: string) =>
      apiClient.post(`/internal/v1/public-health/campaigns/${encodeURIComponent(campaignId)}/dispatch`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-campaigns"] });
    },
  });
}
