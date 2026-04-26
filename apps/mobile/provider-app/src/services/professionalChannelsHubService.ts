/**
 * Professional channels hub — Tier-3 wave 7 BFF (`ProviderProfessionalChannelsHubController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const HUB = "/internal/v1/mobile/provider/professional-channels/hub";

export interface ProfessionalChannelsSection {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
}

export interface ProfessionalChannelsHubPayload {
  refreshed_at: string;
  sections: ProfessionalChannelsSection[];
}

export async function fetchProfessionalChannelsHub(): Promise<ProfessionalChannelsHubPayload> {
  const r = await apiClient.get<{ data: ProfessionalChannelsHubPayload }>(HUB);
  return r.data.data;
}
