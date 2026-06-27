/**
 * Identity-assurance status for the mobile citizen app (G-CZO-05).
 *
 * Reads the caller's CURRENT assurance level from the BFF (which proxies the canonical
 * identity-assurance-service). Used by the dashboard TrustBanner so the mobile app reflects
 * the citizen's real trust level — previously the mobile app never read assurance at all.
 */
import { apiClient } from "@impilo/mobile-api-client";

export interface AssuranceStatus {
  /** Canonical level: LOA1 (account-only) → LOA4 (high-assurance). */
  currentLevel: string;
}

export async function fetchAssuranceStatus(): Promise<AssuranceStatus> {
  const response = await apiClient.get<{ data?: { currentLevel?: string } }>(
    "/internal/v1/identity/assurance/status",
  );
  const level = response.data?.data?.currentLevel ?? "LOA1";
  return { currentLevel: level };
}
