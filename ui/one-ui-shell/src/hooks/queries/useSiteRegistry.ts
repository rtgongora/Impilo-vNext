import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" ? (value as UnknownRecord) : {};
}

function asString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function asBoolean(value: unknown) {
  return typeof value === "boolean" ? value : false;
}

function asDateString(value: unknown) {
  // keep as-is (server returns ISO date); UI can format
  return asString(value);
}

export interface SiteRegistrySummary {
  siteId: string;
  siteCode: string;
  name: string;
  siteType: string;
  siteCategory: string;
  province: string;
  district: string;
  regulatoryStatus: string;
  lifecycleStatus: string;
  licenceStatus: string;
  licenceExpiryDate: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface SiteRegistryProfile {
    site: {
      siteId: string;
      siteCode: string;
      name: string;
      type: string;
      siteCategory: string;
      riskClass: string;
      province: string;
      district: string;
      ward: string;
      regulatoryStatus: string;
      lifecycleStatus: string;
      activeFlag: boolean;
      latitude?: number | null;
      longitude?: number | null;
      geocodeQuality?: string;
    };
  applications: UnknownRecord[];
  operators: UnknownRecord[];
  inspections: UnknownRecord[];
  findings: UnknownRecord[];
  complianceActions: UnknownRecord[];
  licences: UnknownRecord[];
  enforcementCases: UnknownRecord[];
  statusHistory: UnknownRecord[];
  auditEvents: UnknownRecord[];
  /** Present when BFF includes document attachments for the site */
  documents?: UnknownRecord[];
  /** Present when BFF includes staff / assignment rows */
  assignments?: UnknownRecord[];
}

function readNumber(row: UnknownRecord, ...keys: string[]): number | null {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() !== "") {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return null;
}

function normalizeSummary(row: unknown): SiteRegistrySummary {
  const r = asRecord(row);
  return {
    siteId: asString(r.siteId),
    siteCode: asString(r.siteCode),
    name: asString(r.name),
    siteType: asString(r.siteType),
    siteCategory: asString(r.siteCategory),
    province: asString(r.province),
    district: asString(r.district),
    regulatoryStatus: asString(r.regulatoryStatus),
    lifecycleStatus: asString(r.lifecycleStatus),
    licenceStatus: asString(r.licenceStatus),
    licenceExpiryDate: asDateString(r.licenceExpiryDate),
    latitude: readNumber(r, "latitude", "lat", "geoLatitude", "geo_latitude"),
    longitude: readNumber(r, "longitude", "lng", "lon", "geoLongitude", "geo_longitude"),
  };
}

function normalizeProfile(payload: unknown): SiteRegistryProfile {
  const o = asRecord(payload);
  const site = asRecord(o.site);
  return {
    site: {
      siteId: asString(site.siteId),
      siteCode: asString(site.siteCode),
      name: asString(site.name),
      type: asString(site.type),
      siteCategory: asString(site.siteCategory),
      riskClass: asString(site.riskClass),
      province: asString(site.province),
      district: asString(site.district),
      ward: asString(site.ward),
      regulatoryStatus: asString(site.regulatoryStatus),
      lifecycleStatus: asString(site.lifecycleStatus),
      activeFlag: asBoolean(site.activeFlag),
      latitude: readNumber(site, "latitude", "lat"),
      longitude: readNumber(site, "longitude", "lng", "lon"),
      geocodeQuality: asString(site.geocodeQuality),
    },
    applications: Array.isArray(o.applications) ? (o.applications as UnknownRecord[]) : [],
    operators: Array.isArray(o.operators) ? (o.operators as UnknownRecord[]) : [],
    inspections: Array.isArray(o.inspections) ? (o.inspections as UnknownRecord[]) : [],
    findings: Array.isArray(o.findings) ? (o.findings as UnknownRecord[]) : [],
    complianceActions: Array.isArray(o.complianceActions) ? (o.complianceActions as UnknownRecord[]) : [],
    licences: Array.isArray(o.licences) ? (o.licences as UnknownRecord[]) : [],
    enforcementCases: Array.isArray(o.enforcementCases) ? (o.enforcementCases as UnknownRecord[]) : [],
    statusHistory: Array.isArray(o.statusHistory) ? (o.statusHistory as UnknownRecord[]) : [],
    auditEvents: Array.isArray(o.auditEvents) ? (o.auditEvents as UnknownRecord[]) : [],
    documents: Array.isArray(o.documents) ? (o.documents as UnknownRecord[]) : [],
    assignments: Array.isArray(o.assignments) ? (o.assignments as UnknownRecord[]) : [],
  };
}

export function useSiteRegistrySites(params: {
  search?: string;
  regulatoryStatus?: string;
  siteCategory?: string;
  province?: string;
  district?: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ["site-registry-sites", params],
    queryFn: async () => {
      const sp = new URLSearchParams();
      if (params.search) sp.set("search", params.search);
      if (params.regulatoryStatus) sp.set("regulatory_status", params.regulatoryStatus);
      if (params.siteCategory) sp.set("site_category", params.siteCategory);
      if (params.province) sp.set("province", params.province);
      if (params.district) sp.set("district", params.district);
      sp.set("page", String(params.page ?? 0));
      sp.set("size", String(params.size ?? 20));
      const qs = sp.toString();
      const response = await apiClient.get<{ data: unknown }>(
        `/internal/v1/public-health/site-registry/sites${qs ? `?${qs}` : ""}`,
      );
      const data = asRecord(response.data);
      const items = Array.isArray(data.items) ? (data.items as unknown[]) : [];
      return items.map(normalizeSummary);
    },
  });
}

export function useSiteRegistrySiteProfile(siteId: string) {
  return useQuery({
    enabled: siteId.length > 0,
    queryKey: ["site-registry-site", siteId],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>(`/internal/v1/public-health/site-registry/sites/${encodeURIComponent(siteId)}`);
      return normalizeProfile(response.data);
    },
  });
}

export function useCreateSiteApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) => apiClient.post("/internal/v1/public-health/site-registry/applications", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
    },
  });
}

export function useSubmitSiteApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (applicationId: string) =>
      apiClient.post(`/internal/v1/public-health/site-registry/applications/${encodeURIComponent(applicationId)}/submit`, {}),
    onSuccess: (_data, applicationId) => {
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
      void qc.invalidateQueries({ queryKey: ["site-registry-site"] });
      void qc.invalidateQueries({ queryKey: ["site-registry-application", applicationId] });
    },
  });
}

export function useScheduleInspection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) => apiClient.post("/internal/v1/public-health/site-registry/inspections", body),
    onSuccess: (_data, body) => {
      const siteId = asString((body as UnknownRecord).siteId);
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
      if (siteId) void qc.invalidateQueries({ queryKey: ["site-registry-site", siteId] });
    },
  });
}

export function useRecordInspectionOutcome() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { inspectionId: string; body: UnknownRecord }) =>
      apiClient.post(
        `/internal/v1/public-health/site-registry/inspections/${encodeURIComponent(args.inspectionId)}/record`,
        args.body,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
      void qc.invalidateQueries({ queryKey: ["site-registry-site"] });
    },
  });
}

export function useUpdateComplianceAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { actionId: string; body: UnknownRecord }) =>
      apiClient.post(`/internal/v1/public-health/site-registry/compliance-actions/${encodeURIComponent(args.actionId)}`, args.body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["site-registry-site"] });
    },
  });
}

export function useIssueLicence() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) => apiClient.post("/internal/v1/public-health/site-registry/licences", body),
    onSuccess: (_data, body) => {
      const siteId = asString((body as UnknownRecord).siteId);
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
      if (siteId) void qc.invalidateQueries({ queryKey: ["site-registry-site", siteId] });
    },
  });
}

export function useCreateRenewalApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { siteId: string; applicantName?: string }) => {
      const sp = new URLSearchParams();
      if (args.applicantName) sp.set("applicantName", args.applicantName);
      const qs = sp.toString();
      return apiClient.post(
        `/internal/v1/public-health/site-registry/sites/${encodeURIComponent(args.siteId)}/renewals${qs ? `?${qs}` : ""}`,
        {},
      );
    },
    onSuccess: (_data, args) => {
      void qc.invalidateQueries({ queryKey: ["site-registry-site", args.siteId] });
    },
  });
}

export function useOpenEnforcementCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) => apiClient.post("/internal/v1/public-health/site-registry/enforcement-cases", body),
    onSuccess: (_data, body) => {
      const siteId = asString((body as UnknownRecord).siteId);
      if (siteId) void qc.invalidateQueries({ queryKey: ["site-registry-site", siteId] });
    },
  });
}

export function useUploadSiteDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { file: File; siteId: string; documentType: string; applicationId?: string; notes?: string }) => {
      const form = new FormData();
      form.append("file", args.file);
      form.append("siteId", args.siteId);
      form.append("documentType", args.documentType);
      if (args.applicationId) form.append("applicationId", args.applicationId);
      if (args.notes) form.append("notes", args.notes);
      return apiClient.postForm("/internal/v1/public-health/site-registry/documents", form);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["site-registry-site"] });
    },
  });
}

export function useVerifySiteDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { documentId: string; verificationState: string; notes?: string }) =>
      apiClient.post(`/internal/v1/public-health/site-registry/documents/${encodeURIComponent(args.documentId)}/verify`, {
        verificationState: args.verificationState,
        notes: args.notes,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["site-registry-site"] });
    },
  });
}

export function useCreateAssignment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) => apiClient.post("/internal/v1/public-health/site-registry/assignments", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["site-registry-site"] });
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
    },
  });
}

export function useUpdateSiteLocation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { siteId: string; latitude: number; longitude: number; geocodeQuality?: string }) =>
      apiClient.put(`/internal/v1/public-health/site-registry/sites/${encodeURIComponent(args.siteId)}/location`, {
        latitude: args.latitude,
        longitude: args.longitude,
        geocodeQuality: args.geocodeQuality ?? "MANUAL",
      }),
    onSuccess: (_data, args) => {
      void qc.invalidateQueries({ queryKey: ["site-registry-sites"] });
      void qc.invalidateQueries({ queryKey: ["site-registry-site", args.siteId] });
    },
  });
}


