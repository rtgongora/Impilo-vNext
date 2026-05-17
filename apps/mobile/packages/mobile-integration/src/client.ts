/**
 * Integration Service read-only mobile client.
 *
 * Talks to the experience-bff Integration Hub mobile surface. Mobile apps must
 * never call PACS/LIMS/eLMIS/vendor APIs directly; status comes through this
 * client.
 *
 * If the BFF surface is not available (network error or 404), the client
 * returns an empty list rather than throwing — UI then degrades to "status
 * unknown" rather than a red error screen.
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { IntegrationStatusSummary } from "./types";

const PROVIDER_BASE = "/internal/v1/mobile/provider/integration";
const CITIZEN_BASE = "/internal/v1/mobile/citizen/integration";

export type IntegrationCaller = "PROVIDER" | "CITIZEN";

async function safe<T>(p: Promise<{ data: T }>): Promise<T | null> {
  try {
    const res = await p;
    return res.data;
  } catch {
    return null;
  }
}

function unwrap<T>(payload: unknown): T[] {
  if (!payload || typeof payload !== "object") return [];
  const root = payload as Record<string, unknown>;
  if (Array.isArray(root.data)) return root.data as T[];
  if (Array.isArray(root.items)) return root.items as T[];
  return [];
}

export const integrationMobileClient = {
  async listStatuses(caller: IntegrationCaller): Promise<IntegrationStatusSummary[]> {
    const base = caller === "PROVIDER" ? PROVIDER_BASE : CITIZEN_BASE;
    const data = await safe(apiClient.get<unknown>(`${base}/statuses`));
    return unwrap<IntegrationStatusSummary>(data);
  },

  async getStatus(
    caller: IntegrationCaller,
    id: string
  ): Promise<IntegrationStatusSummary | null> {
    const base = caller === "PROVIDER" ? PROVIDER_BASE : CITIZEN_BASE;
    const data = await safe(
      apiClient.get<{ data: IntegrationStatusSummary } | IntegrationStatusSummary>(
        `${base}/statuses/${encodeURIComponent(id)}`
      )
    );
    if (!data) return null;
    const wrap = (data as { data?: IntegrationStatusSummary }).data;
    return (wrap ?? (data as IntegrationStatusSummary)) || null;
  },
};

export type IntegrationMobileClient = typeof integrationMobileClient;
