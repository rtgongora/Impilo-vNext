/**
 * Professional settings hub — Tier-3 wave 6 BFF (`ProviderProfessionalSettingsHubController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const HUB = "/internal/v1/mobile/provider/professional-settings/hub";

export interface ProfessionalSettingsSection {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
}

export interface ProfessionalSettingsHubPayload {
  refreshed_at: string;
  sections: ProfessionalSettingsSection[];
}

export async function fetchProfessionalSettingsHub(): Promise<ProfessionalSettingsHubPayload> {
  const r = await apiClient.get<{ data: ProfessionalSettingsHubPayload }>(HUB);
  return r.data.data;
}
