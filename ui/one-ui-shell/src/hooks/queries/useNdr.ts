"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface NdrBronzeEventRow {
  receipt_id?: string;
  receiptId?: string;
  event_id?: string;
  eventId?: string;
  event_type?: string;
  eventType?: string;
  subject_id?: string;
  subjectId?: string;
  occurred_at?: string;
  occurredAt?: string;
  stored_at?: string;
  storedAt?: string;
}

export interface NdrGoldEncounterRow {
  encounter_id?: string;
  encounterId?: string;
  patient_ref?: string;
  patientRef?: string;
  facility_ref?: string;
  facilityRef?: string;
  status?: string;
  started_at?: string;
  startedAt?: string;
}

export interface NdrPagedResult<T> {
  items: T[];
  hasMore: boolean;
  cursor: string | null;
}

function readString(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "string" && value.length > 0) return value;
  }
  return "";
}

function asBronzeRows(raw: unknown): NdrPagedResult<NdrBronzeEventRow> {
  if (!raw || typeof raw !== "object") return { items: [], hasMore: false, cursor: null };
  const root = raw as Record<string, unknown>;
  const list = Array.isArray(root.items) ? root.items : [];
  return {
    items: list as NdrBronzeEventRow[],
    hasMore: Boolean(root.has_more ?? root.hasMore),
    cursor: typeof root.cursor === "string" ? root.cursor : null,
  };
}

function asGoldRows(raw: unknown): NdrPagedResult<NdrGoldEncounterRow> {
  if (!raw || typeof raw !== "object") return { items: [], hasMore: false, cursor: null };
  const root = raw as Record<string, unknown>;
  const list = Array.isArray(root.items) ? root.items : [];
  return {
    items: list as NdrGoldEncounterRow[],
    hasMore: Boolean(root.has_more ?? root.hasMore),
    cursor: typeof root.cursor === "string" ? root.cursor : null,
  };
}

export function bronzeEventLabel(row: NdrBronzeEventRow): string {
  const record = row as Record<string, unknown>;
  const eventType = readString(record, "event_type", "eventType");
  const eventId = readString(record, "event_id", "eventId");
  return eventType || eventId || "bronze event";
}

export function goldEncounterLabel(row: NdrGoldEncounterRow): string {
  const record = row as Record<string, unknown>;
  const encounterId = readString(record, "encounter_id", "encounterId");
  const patientRef = readString(record, "patient_ref", "patientRef");
  return encounterId ? `${encounterId}${patientRef ? ` · ${patientRef}` : ""}` : patientRef || "encounter";
}

export function useNdrBronzeQuery(filters?: {
  eventType?: string;
  subjectId?: string;
  cursor?: number;
  limit?: number;
}) {
  const cursor = filters?.cursor ?? 0;
  const limit = filters?.limit ?? 25;
  const params = new URLSearchParams({
    cursor: String(cursor),
    limit: String(limit),
  });
  if (filters?.eventType) params.set("event_type", filters.eventType);
  if (filters?.subjectId) params.set("subject_id", filters.subjectId);

  return useQuery({
    queryKey: ["ndr", "bronze", filters?.eventType, filters?.subjectId, cursor, limit],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>(`/internal/v1/ndr/query/bronze?${params}`);
      return asBronzeRows(res.data);
    },
    staleTime: 20_000,
  });
}

export function useNdrGoldEncountersQuery(filters?: {
  patientId?: string;
  cursor?: number;
  limit?: number;
}) {
  const cursor = filters?.cursor ?? 0;
  const limit = filters?.limit ?? 25;
  const params = new URLSearchParams({
    cursor: String(cursor),
    limit: String(limit),
  });
  if (filters?.patientId) params.set("patient_id", filters.patientId);

  return useQuery({
    queryKey: ["ndr", "gold", "encounters", filters?.patientId, cursor, limit],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/ndr/query/gold/encounters?${params}`,
      );
      return asGoldRows(res.data);
    },
    staleTime: 20_000,
  });
}

export function useNdrBuildGoldEncounters() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post<ApiResponse<unknown>>("/internal/v1/ndr/build/gold/encounters", {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["ndr", "gold", "encounters"] });
    },
  });
}
