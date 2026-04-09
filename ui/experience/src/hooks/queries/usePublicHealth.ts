import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" ? (value as UnknownRecord) : {};
}

function asArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
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

function normalizeSignal(resource: unknown): PublicHealthSignal {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    disease: readString(record, "disease", "condition", "name") || "Unspecified signal",
    facility: readString(record, "facility_name", "facility", "source_facility") || "Unknown facility",
    cases: readNumber(record, "case_count", "cases", "count"),
    threshold: readNumber(record, "threshold", "threshold_value"),
    status: readString(record, "status") || "monitoring",
    detectedAt: readString(record, "detected_at", "reported_at", "created_at"),
  };
}

function normalizeCase(resource: unknown): PublicHealthCase {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    disease: readString(record, "disease", "condition") || "Unspecified case",
    patientRef: readString(record, "patient_ref", "cpid", "client_id") || "Unknown patient",
    facility: readString(record, "facility_name", "facility") || "Unknown facility",
    status: readString(record, "status") || "under_review",
    outcome: readString(record, "outcome") || "Pending",
    reportedAt: readString(record, "reported_at", "created_at", "detected_at"),
  };
}

function normalizeAlert(resource: unknown): PublicHealthAlert {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    title:
      readString(record, "title", "name", "alert_name") ||
      readString(record, "disease", "condition") ||
      "Public health alert",
    severity: readString(record, "severity", "priority") || "medium",
    location: readString(record, "location", "area", "jurisdiction") || "Unknown location",
    status: readString(record, "status") || "active",
    detectedAt: readString(record, "detected_at", "created_at", "reported_at"),
  };
}

function normalizeCounter(resource: unknown): PublicHealthCounter {
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

function normalizeCampaign(resource: unknown): PublicHealthCampaign {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    name: readString(record, "name", "campaign_name") || "Unnamed campaign",
    status: readString(record, "status") || "planning",
    campaignType: readString(record, "campaign_type", "type") || "General",
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
  return {
    id: readString(outer, "id") || readString(record, "id"),
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
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/public-health/signals");
      return asArray(response.data).map(normalizeSignal);
    },
  });
}

export function usePublicHealthCases() {
  return useQuery({
    queryKey: ["public-health-cases"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/public-health/cases");
      return asArray(response.data).map(normalizeCase);
    },
  });
}

export function usePublicHealthAlerts() {
  return useQuery({
    queryKey: ["public-health-alerts"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/public-health/alerts");
      return asArray(response.data).map(normalizeAlert);
    },
  });
}

export function usePublicHealthCounters() {
  return useQuery({
    queryKey: ["public-health-counters"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] | UnknownRecord }>("/internal/v1/public-health/counters");
      const raw = response.data;
      if (Array.isArray(raw)) return raw.map(normalizeCounter);
      const record = asRecord(raw);
      return Object.entries(record).map(([key, value]) =>
        normalizeCounter({
          id: key,
          label: key.replace(/_/g, " "),
          value,
        }),
      );
    },
  });
}

export function usePublicHealthCampaigns() {
  return useQuery({
    queryKey: ["public-health-campaigns"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/public-health/campaigns");
      return asArray(response.data).map(normalizeCampaign);
    },
  });
}

export function usePublicHealthSites() {
  return useQuery({
    queryKey: ["public-health-sites"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/public-health/sites");
      return asArray(response.data).map(normalizeSite);
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
