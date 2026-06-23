/**
 * Profile Service — Citizen profile and preferences management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/profile/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { CitizenProfile, ConsentPreference } from "../types";

const V1 = "/internal/v1/mobile/citizen/profile";

export async function fetchProfile(): Promise<CitizenProfile> {
  if (process.env.EXPO_PUBLIC_DEV_BYPASS_AUTH === "true") {
    return {
      cpid: "dev-citizen-001",
      givenName: "Dev",
      familyName: "Citizen",
      dateOfBirth: "1990-01-01",
      sex: "MALE",
      phone: "+263770000000",
      email: "dev@impilo.gov.zw",
      preferredLanguage: "en",
    };
  }
  const response = await apiClient.get<{ data: { attributes: CitizenProfile } }>(V1);
  return response.data.data.attributes;
}

export async function updateProfile(
  updates: Partial<Pick<CitizenProfile, "phone" | "email" | "preferredLanguage" | "avatarUrl">>
): Promise<CitizenProfile> {
  const response = await apiClient.patch<{ data: { attributes: CitizenProfile } }>(V1, updates);
  return response.data.data.attributes;
}

export async function fetchConsents(): Promise<ConsentPreference[]> {
  const response = await apiClient.get<{ data: ConsentPreference[] }>(`${V1}/consents`);
  return response.data.data;
}

export async function updateConsent(
  consentId: string,
  granted: boolean
): Promise<ConsentPreference> {
  const response = await apiClient.patch<{ data: ConsentPreference }>(
    `${V1}/consents/${encodeURIComponent(consentId)}`,
    { granted }
  );
  return response.data.data;
}

export async function deleteAccount(): Promise<void> {
  await apiClient.delete(`${V1}/account`);
}
