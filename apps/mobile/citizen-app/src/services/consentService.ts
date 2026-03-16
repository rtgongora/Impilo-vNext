/**
 * Consent Service — Citizen data sharing consent management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/consents/*)
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface Consent {
  id: string;
  category: string;
  description: string;
  granted: boolean;
  updatedAt: string;
}

const V1 = "/internal/v1/mobile/citizen/consents";

export async function getConsents(): Promise<Consent[]> {
  const response = await apiClient.get<{ data: Consent[] }>(V1);
  return response.data.data;
}

export async function updateConsent(
  consentId: string,
  granted: boolean
): Promise<Consent> {
  const response = await apiClient.patch<{ data: Consent }>(
    `${V1}/${encodeURIComponent(consentId)}`,
    { granted }
  );
  return response.data.data;
}
