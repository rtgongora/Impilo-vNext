/**
 * Regulator Service — facility ↔ regulator (multi-council) relationships.
 *
 * Backend: experience-bff facility-mode regulators (same live routes the web facility regulators
 * page consumes via ui/one-ui-shell/src/components/facility-mode/useRegulators.ts):
 *   - GET  /internal/v1/facility-mode/{facilityId}/regulators
 *   - POST /internal/v1/facility-mode/{facilityId}/regulators   (link a council)
 *
 * Closes part of GAP-19 (multi-council regulator flow had no mobile surface). Real data only.
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/facility-mode";

export interface RegulatorRelationship {
  id: string;
  facilityId: string;
  councilId: string;
  councilCode: string;
  relationshipType: string;
  registrationNumber: string | null;
  status: string;
  validFrom: string | null;
  validTo: string | null;
}

export interface LinkRegulatorInput {
  councilId: string;
  councilCode: string;
  relationshipType?: string;
  registrationNumber?: string;
}

export async function fetchFacilityRegulators(
  facilityId: string | number,
): Promise<RegulatorRelationship[]> {
  const response = await apiClient.get<{ data: RegulatorRelationship[] }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/regulators`,
  );
  return response.data.data;
}

export async function linkRegulator(
  facilityId: string | number,
  input: LinkRegulatorInput,
): Promise<RegulatorRelationship> {
  const response = await apiClient.post<{ data: RegulatorRelationship }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/regulators`,
    input,
  );
  return response.data.data;
}
