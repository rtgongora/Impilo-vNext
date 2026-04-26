/**
 * Developer portal hub — Tier-3 wave 5 BFF (`ProviderDeveloperHubController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const HUB = "/internal/v1/mobile/provider/developer/hub";

export interface DeveloperHubSection {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
}

export interface DeveloperHubPayload {
  refreshed_at: string;
  sections: DeveloperHubSection[];
}

export async function fetchDeveloperHub(): Promise<DeveloperHubPayload> {
  const r = await apiClient.get<{ data: DeveloperHubPayload }>(HUB);
  return r.data.data;
}
