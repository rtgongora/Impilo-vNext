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

export interface CoveragePlan {
  id: string;
  planCode: string;
  planName: string;
  payerId: string;
  planType: string;
  status: string;
}

export interface CoverageMember {
  id: string;
  clientId: string;
  memberNumber: string;
  planId: string;
  relationship: string;
  status: string;
  createdAt: string;
}

export interface CoverageClaim {
  id: string;
  coverageId: string;
  claimType: string;
  facilityId: string;
  totalAmount: number;
  status: string;
  claimNumber: string;
  createdAt: string;
}

export interface CoverageRemittance {
  id: string;
  coverageId: string;
  remittanceNumber: string;
  amount: number;
  currency: string;
  status: string;
  remittedAt: string;
}

export interface CoverageEligibilityResult {
  resultCode: string;
  resultMessage: string;
  coverageStatus: string;
  raw: UnknownRecord;
}

export interface CoveragePreauthResult {
  id: string;
  status: string;
  raw: UnknownRecord;
}

function normalizePlan(resource: unknown): CoveragePlan {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    planCode: readString(record, "plan_code", "planCode"),
    planName: readString(record, "plan_name", "planName") || "Unnamed coverage plan",
    payerId: readString(record, "payer_id", "payerId"),
    planType: readString(record, "plan_type", "planType") || "STANDARD",
    status: readString(record, "status") || "UNKNOWN",
  };
}

function normalizeMember(resource: unknown): CoverageMember {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    clientId: readString(record, "client_id", "clientId"),
    memberNumber: readString(record, "member_number", "memberNumber"),
    planId: readString(record, "plan_id", "planId"),
    relationship: readString(record, "relationship") || "SELF",
    status: readString(record, "status") || "ACTIVE",
    createdAt: readString(record, "created_at", "createdAt"),
  };
}

function normalizeClaim(resource: unknown): CoverageClaim {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    coverageId: readString(record, "coverage_id", "coverageId"),
    claimType: readString(record, "claim_type", "claimType") || "GENERAL",
    facilityId: readString(record, "facility_id", "facilityId"),
    totalAmount: readNumber(record, "total_amount", "totalAmount"),
    status: readString(record, "status") || "SUBMITTED",
    claimNumber:
      readString(record, "claim_number", "claimNumber") ||
      readString(outer, "id") ||
      readString(record, "id"),
    createdAt: readString(record, "created_at", "createdAt"),
  };
}

function normalizeRemittance(resource: unknown): CoverageRemittance {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    coverageId: readString(record, "coverage_id", "coverageId"),
    remittanceNumber:
      readString(record, "remittance_number", "remittanceNumber") ||
      readString(outer, "id") ||
      readString(record, "id"),
    amount: readNumber(record, "amount", "settled_amount", "total_amount"),
    currency: readString(record, "currency") || "USD",
    status: readString(record, "status") || "SETTLED",
    remittedAt: readString(record, "remitted_at", "remittedAt", "created_at", "createdAt"),
  };
}

function normalizeEligibilityResult(resource: unknown): CoverageEligibilityResult {
  const record = getAttributes(resource);
  return {
    resultCode: readString(record, "result_code", "resultCode") || "UNKNOWN",
    resultMessage: readString(record, "result_message", "resultMessage"),
    coverageStatus:
      readString(record, "coverage_status", "coverageStatus") ||
      (readBoolean(record, "eligible", "is_eligible") ? "ACTIVE" : "UNKNOWN"),
    raw: record,
  };
}

function normalizePreauthResult(resource: unknown): CoveragePreauthResult {
  const record = getAttributes(resource);
  const outer = asRecord(resource);
  return {
    id: readString(outer, "id") || readString(record, "id"),
    status: readString(record, "status") || "SUBMITTED",
    raw: record,
  };
}

export function useCoveragePlans() {
  return useQuery({
    queryKey: ["coverage-plans"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/coverage/plans");
      return asArray(response.data).map(normalizePlan);
    },
  });
}

export function useCoverageMembers(planId?: string | null) {
  return useQuery({
    queryKey: ["coverage-members", planId ?? "none"],
    queryFn: async () => {
      if (!planId) return [] as CoverageMember[];
      const response = await apiClient.get<{ data: unknown[] }>(
        `/internal/v1/coverage/members?plan_id=${encodeURIComponent(planId)}`,
      );
      return asArray(response.data).map(normalizeMember);
    },
    enabled: Boolean(planId),
  });
}

export function useCoverageClaims(coverageId?: string | null) {
  return useQuery({
    queryKey: ["coverage-claims", coverageId ?? "none"],
    queryFn: async () => {
      if (!coverageId) return [] as CoverageClaim[];
      const response = await apiClient.get<{ data: unknown[] }>(
        `/internal/v1/coverage/claims?coverageId=${encodeURIComponent(coverageId)}`,
      );
      return asArray(response.data).map(normalizeClaim);
    },
    enabled: Boolean(coverageId),
  });
}

export function useCoverageRemittances() {
  return useQuery({
    queryKey: ["coverage-remittances"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>("/internal/v1/coverage/remittances");
      return asArray(response.data).map(normalizeRemittance);
    },
  });
}

// Phase 6 — Slice 6A. Read-only list hooks for the four CoverageController
// list endpoints that the BFF already exposes but no UI hook consumes yet.
// Each is envelope-tolerant (top-level array, `{ data: [] }`, `{ items: [] }`)
// and returns `unknown[]` so consumers must defensively probe fields rather
// than rely on a typed shape that has not yet been agreed across services.
// See docs/audits/phase-6-claims-coverage-remittance-subsidy-audit.md.
export function coverageListRows(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === "object") {
    const root = payload as Record<string, unknown>;
    if (Array.isArray(root.data)) return root.data;
    if (Array.isArray(root.items)) return root.items;
    if (Array.isArray(root.content)) return root.content;
  }
  return [];
}

export function useCoverageContributionsList() {
  return useQuery({
    queryKey: ["coverage-contributions-list"],
    queryFn: async () => {
      const res = await apiClient.get<unknown>("/internal/v1/coverage/contributions");
      return coverageListRows(res);
    },
  });
}

export function useCoveragePreauthsList() {
  return useQuery({
    queryKey: ["coverage-preauths-list"],
    queryFn: async () => {
      const res = await apiClient.get<unknown>("/internal/v1/coverage/preauths");
      return coverageListRows(res);
    },
  });
}

export function useCoverageUtilizationList() {
  return useQuery({
    queryKey: ["coverage-utilization-list"],
    queryFn: async () => {
      const res = await apiClient.get<unknown>("/internal/v1/coverage/utilization");
      return coverageListRows(res);
    },
  });
}

export function useCoverageAppealsList() {
  return useQuery({
    queryKey: ["coverage-appeals-list"],
    queryFn: async () => {
      const res = await apiClient.get<unknown>("/internal/v1/coverage/appeals");
      return coverageListRows(res);
    },
  });
}

export function useCheckCoverageEligibility() {
  return useMutation({
    mutationFn: async (body: Record<string, string>) => {
      const response = await apiClient.post<{ data: unknown }>("/internal/v1/coverage/eligibility", body);
      return normalizeEligibilityResult(response.data);
    },
  });
}

export function useEnrollCoverageMember() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/coverage/members", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["coverage-members"] });
    },
  });
}

export function useCreateCoverageClaim() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/coverage/claims", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["coverage-claims"] });
    },
  });
}

export function useCreateCoveragePreauth() {
  return useMutation({
    mutationFn: async (body: Record<string, string>) => {
      const response = await apiClient.post<{ data: unknown }>("/internal/v1/coverage/preauth", body);
      return normalizePreauthResult(response.data);
    },
  });
}
