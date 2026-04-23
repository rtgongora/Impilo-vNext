"use client";

import { useCallback, useState } from "react";
import { apiClient } from "@/lib/api-client";
import type {
  IntelligenceQueryRequest,
  IntelligenceQueryResponseEnvelope,
  IntelligenceQueryType,
} from "@/lib/intelligence/types";

export function useIntelligenceQuery() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (req: IntelligenceQueryRequest) => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.post<IntelligenceQueryResponseEnvelope>(
        "/internal/v1/intelligence-plane/query",
        req,
      );
      return res?.data ?? null;
    } catch (e: unknown) {
      const msg =
        e && typeof e === "object" && "error" in e
          ? String((e as { error?: { message?: string } }).error?.message ?? "Request failed")
          : "Request failed";
      setError(msg);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return { run, loading, error };
}

export async function fetchShellFusionHints(q: string, facilityId?: string | null) {
  const sp = new URLSearchParams({ q, rank_mode: "lexical" });
  if (facilityId) sp.set("facility_id", facilityId);
  return apiClient.get<IntelligenceQueryResponseEnvelope>(
    `/internal/v1/intelligence-plane/shell-suggest?${sp.toString()}`,
  );
}

/** One-shot query helper for components that do not need loading state. */
export async function postIntelligencePlane(
  queryType: IntelligenceQueryType,
  context?: Record<string, unknown>,
  input?: Record<string, unknown>,
) {
  const res = await apiClient.post<IntelligenceQueryResponseEnvelope>(
    "/internal/v1/intelligence-plane/query",
    { queryType, context: context ?? {}, input: input ?? {} },
  );
  return res?.data ?? null;
}
