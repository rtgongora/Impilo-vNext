/**
 * Admin / registry hub — Tier-3 wave 3 BFF (`ProviderAdminRegistryController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const HUB = "/internal/v1/mobile/provider/admin-registry/hub";

export interface AdminRegistrySection {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
}

export interface AdminRegistryHubPayload {
  refreshed_at: string;
  sections: AdminRegistrySection[];
}

export async function fetchAdminRegistryHub(): Promise<AdminRegistryHubPayload> {
  const r = await apiClient.get<{ data: AdminRegistryHubPayload }>(HUB);
  return r.data.data;
}
