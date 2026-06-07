/**
 * Facility directory — shared citizen facility search for booking flows.
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface FacilitySummary {
  id: string;
  name: string;
  facilityType?: string;
  district?: string;
  province?: string;
}

interface FacilityResource {
  id: string;
  attributes: {
    name: string;
    facility_type?: string;
    district?: string;
    province?: string;
  };
}

export async function fetchFacilities(search?: string): Promise<FacilitySummary[]> {
  const params = search ? `?search=${encodeURIComponent(search)}` : "";
  const response = await apiClient.get<{ data: FacilityResource[] }>(`/internal/v1/facilities${params}`);
  return response.data.data.map((facility) => ({
    id: facility.id,
    name: facility.attributes.name,
    facilityType: facility.attributes.facility_type,
    district: facility.attributes.district,
    province: facility.attributes.province,
  }));
}
