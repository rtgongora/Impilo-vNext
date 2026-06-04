import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { randomUUID } from "@/lib/uuid";

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" ? (value as UnknownRecord) : {};
}

function unwrapEnvelope(value: unknown): unknown {
  const record = asRecord(value);
  return record.data ?? value;
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

export interface PublicHealthInspectionRow {
  inspectionId: string;
  siteId: string;
  siteName: string;
  siteType: string;
  inspectionType: string;
  scheduledDate: string;
  actualDate: string;
  status: string;
  outcome: string;
  scorePercent: number;
  criticalFailCount: number;
}

export interface PublicHealthComplianceActionRow {
  actionId: string;
  siteId: string;
  siteName: string;
  siteType: string;
  actionType: string;
  ownerRef: string;
  dueDate: string;
  status: string;
}

export interface PublicHealthEnforcementCaseRow {
  caseId: string;
  siteId: string;
  siteName: string;
  siteType: string;
  triggerType: string;
  status: string;
  recommendation: string;
  authorityRoute: string;
}

export interface PublicHealthInspectionSchedule {
  id: string;
  siteId: string;
  inspectionType: string;
  scheduledAt: string;
  responsibleRef: string;
  priority: string;
  reason: string;
  scopeRef: string;
  recurrenceRule: string;
  notes: string;
  notificationPreference: string;
  status: string;
}

export interface CreatePublicHealthSignalPayload {
  name: string;
  description?: string;
  eventType: string;
  conditionField?: string;
  threshold: number;
  windowHours: number;
  severity: string;
}

export interface IngestPublicHealthEventPayload {
  eventType: string;
  payload: string;
  facilityId?: string | null;
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
      randomUUID(),
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

function normalizeInspectionRow(resource: unknown): PublicHealthInspectionRow {
  const r = getAttributes(resource);
  return {
    inspectionId: readString(r, "inspectionId", "inspection_id", "id"),
    siteId: readString(r, "siteId", "site_id"),
    siteName: readString(r, "siteName", "site_name") || "Unknown site",
    siteType: readString(r, "siteType", "site_type") || "Unknown",
    inspectionType: readString(r, "inspectionType", "inspection_type") || "Routine",
    scheduledDate: readString(r, "scheduledDate", "scheduled_date"),
    actualDate: readString(r, "actualDate", "actual_date"),
    status: readString(r, "status") || "SCHEDULED",
    outcome: readString(r, "outcome") || "—",
    scorePercent: readNumber(r, "scorePercent", "score_percent"),
    criticalFailCount: readNumber(r, "criticalFailCount", "critical_fail_count"),
  };
}

function normalizeComplianceActionRow(resource: unknown): PublicHealthComplianceActionRow {
  const r = getAttributes(resource);
  return {
    actionId: readString(r, "actionId", "action_id", "id"),
    siteId: readString(r, "siteId", "site_id"),
    siteName: readString(r, "siteName", "site_name") || "Unknown site",
    siteType: readString(r, "siteType", "site_type") || "Unknown",
    actionType: readString(r, "actionType", "action_type") || "RECTIFY_FINDING",
    ownerRef: readString(r, "ownerRef", "owner_ref") || "—",
    dueDate: readString(r, "dueDate", "due_date"),
    status: readString(r, "status") || "OPEN",
  };
}

function normalizeEnforcementCaseRow(resource: unknown): PublicHealthEnforcementCaseRow {
  const r = getAttributes(resource);
  return {
    caseId: readString(r, "caseId", "case_id", "id"),
    siteId: readString(r, "siteId", "site_id"),
    siteName: readString(r, "siteName", "site_name") || "Unknown site",
    siteType: readString(r, "siteType", "site_type") || "Unknown",
    triggerType: readString(r, "triggerType", "trigger_type") || "INSPECTION_FAILURE",
    status: readString(r, "status") || "OPEN",
    recommendation: readString(r, "recommendation") || "—",
    authorityRoute: readString(r, "authorityRoute", "authority_route") || "—",
  };
}

function normalizeInspectionSchedule(resource: unknown): PublicHealthInspectionSchedule {
  const r = getAttributes(resource);
  return {
    id: readString(r, "id"),
    siteId: readString(r, "siteId", "site_id"),
    inspectionType: readString(r, "inspectionType", "inspection_type") || "GENERAL",
    scheduledAt: readString(r, "scheduledAt", "scheduled_at"),
    responsibleRef: readString(r, "responsibleRef", "responsible_ref"),
    priority: readString(r, "priority") || "MEDIUM",
    reason: readString(r, "reason"),
    scopeRef: readString(r, "scopeRef", "scope_ref"),
    recurrenceRule: readString(r, "recurrenceRule", "recurrence_rule"),
    notes: readString(r, "notes"),
    notificationPreference: readString(r, "notificationPreference", "notification_preference"),
    status: readString(r, "status") || "SCHEDULED",
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

/** Normalizes weekly IDSR aggregate payloads (BFF GET /public-health/weekly-idsr). */
export function parseWeeklyIdsrPayload(raw: unknown): PublicHealthCounter[] {
  const rows = extractPublicHealthList(raw, ["rows", "items", "counters"]);
  if (rows.length > 0) {
    return rows.map((row) => {
      const r = asRecord(row);
      const syndrome = readString(r, "syndromeCode", "syndrome_code", "label") || "Syndrome";
      const district = readString(r, "district", "facility", "facility_id");
      const weekStart = readString(r, "weekStart", "week_start", "count_date");
      return normalizeCounter({
        id: readString(r, "id") || `${syndrome}-${district}-${weekStart}`,
        label: syndrome,
        value: String(readNumber(r, "total", "event_count", "count", "value")),
        detail: [district, weekStart].filter(Boolean).join(" · "),
      });
    });
  }
  return parseCountersPayload(raw);
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
      return parseWeeklyIdsrPayload(response.data);
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

export function usePublicHealthSiteRegistryInspections(status?: string) {
  return useQuery({
    queryKey: ["public-health-site-registry-inspections", status ?? ""],
    queryFn: async () => {
      const q = status ? `?status=${encodeURIComponent(status)}&size=100` : "?size=100";
      const response = await apiClient.get<{ data: unknown }>(`/internal/v1/public-health/site-registry/inspections${q}`);
      return parseRegistryInspectionPayload(unwrapEnvelope(response.data));
    },
  });
}

export function usePublicHealthSiteRegistryComplianceActions(status?: string) {
  return useQuery({
    queryKey: ["public-health-site-registry-compliance-actions", status ?? ""],
    queryFn: async () => {
      const q = status ? `?status=${encodeURIComponent(status)}&size=100` : "?size=100";
      const response = await apiClient.get<{ data: unknown }>(`/internal/v1/public-health/site-registry/compliance-actions${q}`);
      return parseRegistryCompliancePayload(unwrapEnvelope(response.data));
    },
  });
}

export function usePublicHealthSiteRegistryEnforcementCases(status?: string) {
  return useQuery({
    queryKey: ["public-health-site-registry-enforcement-cases", status ?? ""],
    queryFn: async () => {
      const q = status ? `?status=${encodeURIComponent(status)}&size=100` : "?size=100";
      const response = await apiClient.get<{ data: unknown }>(`/internal/v1/public-health/site-registry/enforcement-cases${q}`);
      return parseRegistryEnforcementPayload(unwrapEnvelope(response.data));
    },
  });
}

export function parseRegistryInspectionPayload(raw: unknown): PublicHealthInspectionRow[] {
  return extractPublicHealthList(raw, ["items"]).map(normalizeInspectionRow);
}

export function parseRegistryCompliancePayload(raw: unknown): PublicHealthComplianceActionRow[] {
  return extractPublicHealthList(raw, ["items"]).map(normalizeComplianceActionRow);
}

export function parseRegistryEnforcementPayload(raw: unknown): PublicHealthEnforcementCaseRow[] {
  return extractPublicHealthList(raw, ["items"]).map(normalizeEnforcementCaseRow);
}

export function usePublicHealthInspectionSchedules() {
  return useQuery({
    queryKey: ["public-health-inspection-schedules"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/inspections/schedules");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeInspectionSchedule);
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

export function useCreatePublicHealthInspectionSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/public-health/inspections/schedules", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-inspection-schedules"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-site-registry-inspections"] });
    },
  });
}

export function useCreatePublicHealthSignal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePublicHealthSignalPayload) =>
      apiClient.post("/internal/v1/public-health/signals", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-signals"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-counters"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-weekly-idsr"] });
    },
  });
}

export function useAcknowledgePublicHealthAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (alertId: string) =>
      apiClient.post(`/internal/v1/public-health/alerts/${encodeURIComponent(alertId)}/acknowledge`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-alerts"] });
    },
  });
}

export function useIngestPublicHealthEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: IngestPublicHealthEventPayload) => apiClient.post("/internal/v1/public-health/ingest", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-cases"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-counters"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-alerts"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-weekly-idsr"] });
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

export interface PublicHealthOperationsHome {
  reportType: string;
  kpis: Record<string, number>;
  priorityWorklist: Array<{ type: string; id: string; label: string; priority: string }>;
  mapMarkerCount: number;
}

export interface PublicHealthOutbreak {
  id: string;
  name: string;
  lifecycleStatus: string;
  severity: string;
  province: string;
  district: string;
  latitude?: number;
  longitude?: number;
}

export interface PublicHealthFieldTask {
  id: string;
  title: string;
  status: string;
  taskType: string;
  assignedTo: string;
  facilityId: string;
}

export interface PublicHealthMapMarker {
  id: string;
  markerType: string;
  label: string;
  latitude: number;
  longitude: number;
  status: string;
}

export interface PublicHealthBrief {
  id: string;
  title: string;
  briefType: string;
  status: string;
  summaryMarkdown: string;
}

export interface NompiloPhSummary {
  message: string;
  suggestions: string[];
  source: string;
  provider: string;
  fallbackUsed: boolean;
  deterministicOnly: boolean;
  confidence: number;
  requiresHumanApproval: boolean;
  auditRef: string;
}

export interface LlmProviderHealth {
  provider: string;
  enabled: boolean;
  healthy: boolean;
  avgLatencyMs: number;
  lastSuccessAt: string;
  lastFailureAt: string;
  lastError: string;
}

export interface PublicHealthAlertRule {
  id: string;
  title: string;
  active: boolean;
  thresholdValue: number;
}

export interface PublicHealthDataSourceStatus {
  id: string;
  sourceName: string;
  healthStatus: string;
  latencySeconds: number;
  errorCount24h: number;
}

export interface PublicHealthDataQualityIssue {
  id: string;
  issueType: string;
  severity: string;
  title: string;
  status: string;
}

function normalizeOutbreak(resource: unknown): PublicHealthOutbreak {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id ?? record.outbreak_id ?? record.outbreakId;
  return {
    id: idVal != null ? String(idVal) : "",
    name: readString(record, "name", "title") || "Outbreak",
    lifecycleStatus: readString(record, "lifecycle_status", "lifecycleStatus") || "RECORDED",
    severity: readString(record, "severity") || "HIGH",
    province: readString(record, "province") || "—",
    district: readString(record, "district") || "—",
    latitude: readNumber(record, "latitude") || undefined,
    longitude: readNumber(record, "longitude") || undefined,
  };
}

function normalizeFieldTask(resource: unknown): PublicHealthFieldTask {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id ?? record.task_id ?? record.taskId;
  return {
    id: idVal != null ? String(idVal) : "",
    title: readString(record, "title") || "Field task",
    status: readString(record, "status") || "PLANNED",
    taskType: readString(record, "task_type", "taskType") || "FIELD_OPERATION",
    assignedTo: readString(record, "assigned_to", "assignedTo") || "—",
    facilityId: readString(record, "facility_id", "facilityId") || "—",
  };
}

function normalizeMapMarker(resource: unknown): PublicHealthMapMarker {
  const record = getAttributes(resource);
  return {
    id: String(record.id ?? ""),
    markerType: readString(record, "marker_type", "markerType") || "unknown",
    label: readString(record, "label") || "Marker",
    latitude: readNumber(record, "latitude"),
    longitude: readNumber(record, "longitude"),
    status: readString(record, "status") || "—",
  };
}

function normalizeBrief(resource: unknown): PublicHealthBrief {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id ?? record.brief_id ?? record.briefId;
  return {
    id: idVal != null ? String(idVal) : "",
    title: readString(record, "title") || "Brief",
    briefType: readString(record, "brief_type", "briefType") || "SITUATION",
    status: readString(record, "status") || "DRAFT",
    summaryMarkdown: readString(record, "summary_markdown", "summaryMarkdown") || "",
  };
}

function normalizeOperationsHome(raw: unknown): PublicHealthOperationsHome {
  const record = asRecord(raw);
  const kpisRaw = asRecord(record.kpis);
  const kpis: Record<string, number> = {};
  for (const [key, value] of Object.entries(kpisRaw)) {
    kpis[key] = readNumber(kpisRaw, key);
  }
  const worklist = extractPublicHealthList(record, ["priority_worklist", "priorityWorklist"]).map((item) => {
    const r = asRecord(item);
    return {
      type: readString(r, "type"),
      id: String(r.id ?? ""),
      label: readString(r, "label"),
      priority: readString(r, "priority"),
    };
  });
  return {
    reportType: readString(record, "report_type", "reportType") || "OPERATIONS_HOME",
    kpis,
    priorityWorklist: worklist,
    mapMarkerCount: readNumber(record, "map_marker_count", "mapMarkerCount"),
  };
}

export function usePublicHealthOperationsHome() {
  return useQuery({
    queryKey: ["public-health-operations-home"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/operations-home");
      return normalizeOperationsHome(unwrapEnvelope(response.data));
    },
  });
}

export function usePublicHealthOutbreaks() {
  return useQuery({
    queryKey: ["public-health-outbreaks"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/outbreaks");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeOutbreak);
    },
  });
}

export function useTransitionPublicHealthOutbreak() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ outbreakId, lifecycleStatus }: { outbreakId: string; lifecycleStatus: string }) =>
      apiClient.post(
        `/internal/v1/public-health/outbreaks/${encodeURIComponent(outbreakId)}/transition`,
        { lifecycle_status: lifecycleStatus },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-outbreaks"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-operations-home"] });
    },
  });
}

export function usePublicHealthFieldTasks() {
  return useQuery({
    queryKey: ["public-health-field-tasks"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/field-tasks");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeFieldTask);
    },
  });
}

export function useTransitionPublicHealthFieldTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, status, assignedTo }: { taskId: string; status: string; assignedTo?: string }) =>
      apiClient.post(`/internal/v1/public-health/field-tasks/${encodeURIComponent(taskId)}/transition`, {
        status,
        assigned_to: assignedTo,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-field-tasks"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-operations-home"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-map-markers"] });
    },
  });
}

export function usePublicHealthMapMarkers() {
  return useQuery({
    queryKey: ["public-health-map-markers"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/map-markers");
      const record = asRecord(unwrapEnvelope(response.data));
      return extractPublicHealthList(record, ["markers"]).map(normalizeMapMarker);
    },
  });
}

export function usePublicHealthBriefs() {
  return useQuery({
    queryKey: ["public-health-briefs"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/reports/briefs");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeBrief);
    },
  });
}

export function useGeneratePublicHealthBrief() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { title?: string; briefType?: string }) =>
      apiClient.post("/internal/v1/public-health/reports/briefs/generate", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-briefs"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-operations-home"] });
    },
  });
}

export function usePublishPublicHealthBrief() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (briefId: string) =>
      apiClient.post(`/internal/v1/public-health/reports/briefs/${encodeURIComponent(briefId)}/publish`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-briefs"] });
    },
  });
}

export function useNompiloPublicHealthSummary() {
  return useQuery({
    queryKey: ["public-health-nompilo-summary"],
    queryFn: async () => {
      const response = await apiClient.post<{ data: unknown }>(
        "/internal/v1/public-health/nompilo/situation-summary",
        {},
      );
      const record = asRecord(unwrapEnvelope(response.data));
      const suggestions = record.suggestions;
      const structured = asRecord(record.structuredOutput);
      return {
        message: readString(record, "message", "content") || readString(structured, "summary"),
        suggestions:
          Array.isArray(suggestions) && suggestions.length > 0
            ? (suggestions as string[])
            : (Array.isArray(structured.recommendedActions) ? (structured.recommendedActions as string[]) : []),
        source: readString(record, "source") || "HYBRID_LLM_OR_DETERMINISTIC",
        provider: readString(record, "provider") || "deterministic",
        fallbackUsed: readBoolean(record, "fallbackUsed"),
        deterministicOnly: readBoolean(record, "deterministicOnly"),
        confidence: readNumber(structured, "confidence"),
        requiresHumanApproval: readBoolean(record, "requiresHumanApproval") || readBoolean(structured, "requiresHumanApproval"),
        auditRef: readString(record, "auditRef"),
      } satisfies NompiloPhSummary;
    },
    staleTime: 60_000,
  });
}

export function useLlmProviderHealth() {
  return useQuery({
    queryKey: ["llm-provider-health"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/llm/providers/health");
      const rows = extractPublicHealthList(unwrapEnvelope(response.data), ["items"]);
      return rows.map((row) => {
        const r = asRecord(row);
        return {
          provider: readString(r, "provider"),
          enabled: readBoolean(r, "enabled"),
          healthy: readBoolean(r, "healthy"),
          avgLatencyMs: readNumber(r, "avgLatencyMs", "avg_latency_ms"),
          lastSuccessAt: readString(r, "lastSuccessAt", "last_success_at"),
          lastFailureAt: readString(r, "lastFailureAt", "last_failure_at"),
          lastError: readString(r, "lastError", "last_error"),
        } satisfies LlmProviderHealth;
      });
    },
    staleTime: 30_000,
  });
}

function normalizeAlertRule(resource: unknown): PublicHealthAlertRule {
  const r = getAttributes(resource);
  return {
    id: String((asRecord(resource).id ?? r.id ?? "")),
    title: readString(r, "title") || "Alert rule",
    active: readBoolean(r, "active"),
    thresholdValue: readNumber(r, "thresholdValue", "threshold_value"),
  };
}

function normalizeDataSource(resource: unknown): PublicHealthDataSourceStatus {
  const r = getAttributes(resource);
  return {
    id: String((asRecord(resource).id ?? r.id ?? "")),
    sourceName: readString(r, "sourceName", "source_name") || "Data source",
    healthStatus: readString(r, "healthStatus", "health_status") || "HEALTHY",
    latencySeconds: readNumber(r, "latencySeconds", "latency_seconds"),
    errorCount24h: readNumber(r, "errorCount24h", "error_count_24h"),
  };
}

function normalizeDataQualityIssue(resource: unknown): PublicHealthDataQualityIssue {
  const r = getAttributes(resource);
  return {
    id: String((asRecord(resource).id ?? r.id ?? "")),
    issueType: readString(r, "issueType", "issue_type") || "DATA_QUALITY",
    severity: readString(r, "severity") || "MEDIUM",
    title: readString(r, "title") || "Issue",
    status: readString(r, "status") || "OPEN",
  };
}

export function usePublicHealthAlertRules() {
  return useQuery({
    queryKey: ["public-health-intelligence-alert-rules"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/intelligence/alert-rules");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeAlertRule);
    },
  });
}

export function usePublicHealthDataSources() {
  return useQuery({
    queryKey: ["public-health-intelligence-data-sources"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/intelligence/data-sources");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeDataSource);
    },
  });
}

export function usePublicHealthDataQualityIssues() {
  return useQuery({
    queryKey: ["public-health-intelligence-data-quality-issues"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/intelligence/data-quality-issues");
      return extractPublicHealthList(unwrapEnvelope(response.data), ["items"]).map(normalizeDataQualityIssue);
    },
  });
}

function invalidatePhLifecycle(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ["public-health-outbreaks"] });
  queryClient.invalidateQueries({ queryKey: ["public-health-field-tasks"] });
  queryClient.invalidateQueries({ queryKey: ["public-health-operations-home"] });
  queryClient.invalidateQueries({ queryKey: ["public-health-map-markers"] });
}

export interface PublicHealthComplaint {
  id: string;
  title: string;
  category: string;
  status: string;
  province: string;
  district: string;
}

export interface PublicHealthContext {
  activeJurisdictionPack: string;
  jurisdictionPacks: Array<{ id: string; label: string; description: string }>;
  workspaceId: string;
  recommendedPurposeOfUse: string;
  visibilityProfile: string;
}

export interface PublicHealthPreferences {
  jurisdictionPack: string;
}

const COMPLAINT_TRANSITIONS: Record<string, string> = {
  RECEIVED: "TRIAGED",
  TRIAGED: "INVESTIGATING",
  INVESTIGATING: "RESOLVED",
  RESOLVED: "CLOSED",
};

export { COMPLAINT_TRANSITIONS };

function normalizeComplaint(resource: unknown): PublicHealthComplaint {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  const idVal = outer.id ?? record.id ?? record.complaint_id ?? record.complaintId;
  return {
    id: idVal != null ? String(idVal) : "",
    title: readString(record, "title") || "Complaint",
    category: readString(record, "category") || "NUISANCE",
    status: readString(record, "status") || "RECEIVED",
    province: readString(record, "province") || "—",
    district: readString(record, "district") || "—",
  };
}

export function usePublicHealthContext(jurisdictionPack?: string) {
  return useQuery({
    queryKey: ["public-health-context", jurisdictionPack ?? ""],
    queryFn: async () => {
      const q = jurisdictionPack ? `?jurisdiction_pack=${encodeURIComponent(jurisdictionPack)}` : "";
      const response = await apiClient.get<{ data: unknown }>(`/internal/v1/public-health/context${q}`);
      const record = asRecord(unwrapEnvelope(response.data));
      const packs = extractPublicHealthList(record, ["jurisdiction_packs", "jurisdictionPacks"]).map((p) => {
        const r = asRecord(p);
        return {
          id: readString(r, "id"),
          label: readString(r, "label"),
          description: readString(r, "description"),
        };
      });
      return {
        activeJurisdictionPack: readString(record, "active_jurisdiction_pack", "activeJurisdictionPack") || "national",
        jurisdictionPacks: packs,
        workspaceId: readString(record, "workspace_id", "workspaceId"),
        recommendedPurposeOfUse: readString(record, "recommended_purpose_of_use", "recommendedPurposeOfUse") || "PUBLIC_HEALTH",
        visibilityProfile: readString(record, "visibility_profile", "visibilityProfile"),
      } satisfies PublicHealthContext;
    },
  });
}

export function usePublicHealthPreferences() {
  return useQuery({
    queryKey: ["public-health-preferences"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/preferences");
      const record = asRecord(unwrapEnvelope(response.data));
      return {
        jurisdictionPack: readString(record, "jurisdiction_pack", "jurisdictionPack") || "national",
      } satisfies PublicHealthPreferences;
    },
  });
}

export function useSavePublicHealthPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { jurisdictionPack: string }) =>
      apiClient.put("/internal/v1/public-health/preferences", {
        jurisdiction_pack: body.jurisdictionPack,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-preferences"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-context"] });
    },
  });
}

export function usePublicHealthComplaints() {
  return useQuery({
    queryKey: ["public-health-complaints"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/complaints");
      return extractPublicHealthList(response.data, ["items"]).map(normalizeComplaint);
    },
  });
}

export function useFilePublicHealthComplaint() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/public-health/complaints", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-complaints"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-operations-home"] });
    },
  });
}

export function useTransitionPublicHealthComplaint() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ complaintId, status }: { complaintId: string; status: string }) =>
      apiClient.post(`/internal/v1/public-health/complaints/${encodeURIComponent(complaintId)}/transition`, {
        status,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["public-health-complaints"] });
      queryClient.invalidateQueries({ queryKey: ["public-health-operations-home"] });
    },
  });
}

export function useCreatePublicHealthOutbreak() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/public-health/outbreaks", body),
    onSuccess: () => invalidatePhLifecycle(queryClient),
  });
}

export function useEvaluatePublicHealthBriefExport() {
  return useMutation({
    mutationFn: ({ briefId, format }: { briefId: string; format: "PDF" | "CSV" }) =>
      apiClient.post(`/internal/v1/public-health/reports/briefs/${encodeURIComponent(briefId)}/exports/evaluate`, {
        format,
        purpose: "PUBLIC_HEALTH",
      }),
  });
}

export interface PublicHealthBriefExportResult {
  fileName: string;
  contentType: string;
  contentBase64: string;
  format: string;
}

export function useExportPublicHealthBrief() {
  return useMutation({
    mutationFn: async ({ briefId, format }: { briefId: string; format: "PDF" | "CSV" }) => {
      const response = await apiClient.post<{ data: unknown }>(
        `/internal/v1/public-health/reports/briefs/${encodeURIComponent(briefId)}/exports`,
        { format, purpose: "PUBLIC_HEALTH" },
      );
      const root = asRecord(unwrapEnvelope(response.data));
      const exportRecord = asRecord(root.export);
      return {
        fileName: readString(exportRecord, "file_name", "fileName") || `brief-${briefId}.${format.toLowerCase()}`,
        contentType: readString(exportRecord, "content_type", "contentType") || "application/octet-stream",
        contentBase64: readString(exportRecord, "content_base64", "contentBase64"),
        format: readString(exportRecord, "format") || format,
      } satisfies PublicHealthBriefExportResult;
    },
  });
}
