/**
 * Operations + reports hub — Tier-3 wave 4 BFF (`ProviderOpsReportsController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const HUB = "/internal/v1/mobile/provider/ops-reports/hub";

export interface OpsReportsSection {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
}

export interface OpsReportsHubPayload {
  refreshed_at: string;
  sections: OpsReportsSection[];
}

export async function fetchOpsReportsHub(): Promise<OpsReportsHubPayload> {
  const r = await apiClient.get<{ data: OpsReportsHubPayload }>(HUB);
  return r.data.data;
}
