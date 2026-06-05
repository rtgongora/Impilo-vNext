"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type AnyRecord = Record<string, unknown>;

function asList(raw: unknown): AnyRecord[] {
  if (Array.isArray(raw)) {
    return raw.filter((x): x is AnyRecord => typeof x === "object" && x !== null);
  }
  if (raw && typeof raw === "object") {
    const record = raw as AnyRecord;
    if (Array.isArray(record.items)) return record.items as AnyRecord[];
    if (Array.isArray(record.content)) return record.content as AnyRecord[];
    if (Array.isArray(record.data)) return record.data as AnyRecord[];
  }
  return [];
}

export function useCitizenHealthSummary() {
  const query = useQuery({
    queryKey: ["citizen", "health-summary"],
    queryFn: () =>
      apiClient.get<ApiResponse<AnyRecord>>("/internal/v1/citizen/health-summary"),
  });

  const summary = (query.data?.data ?? {}) as AnyRecord;
  const conditions = useMemo(() => asList(summary.conditions), [summary.conditions]);
  const allergies = useMemo(() => asList(summary.allergies), [summary.allergies]);
  const results = useMemo(
    () => asList(summary.results ?? summary.labs ?? summary.observations),
    [summary.results, summary.labs, summary.observations],
  );

  return { ...query, summary, conditions, allergies, results };
}
