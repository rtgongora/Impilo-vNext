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
  /**
   * tuso regulatory_status. `IMPORTED_PENDING_CONFIGURATION` means the facility
   * is HPA-registered but NOBODY has confirmed its services, hours or contact
   * details — it must never be presented as operating. See
   * components/FacilityDisclosure.tsx.
   */
  regulatoryStatus?: string | null;
}

interface FacilityResource {
  id: string;
  attributes: {
    name: string;
    facilityType?: string;
    facility_type?: string;
    district?: string;
    province?: string;
    latitude?: number | null;
    longitude?: number | null;
    ownership?: string | null;
    level?: string | null;
    hasValidCoordinates?: boolean;
    regulatoryStatus?: string | null;
    regulatory_status?: string | null;
  };
}

export async function fetchFacilities(search?: string): Promise<FacilitySummary[]> {
  const params = search ? `?search=${encodeURIComponent(search)}` : "";
  const response = await apiClient.get<{ data: FacilityResource[] }>(`/internal/v1/facilities${params}`);
  return response.data.data.map((facility) => ({
    id: facility.id,
    name: facility.attributes.name,
    facilityType: facility.attributes.facilityType ?? facility.attributes.facility_type,
    district: facility.attributes.district,
    province: facility.attributes.province,
    regulatoryStatus: facility.attributes.regulatoryStatus ?? facility.attributes.regulatory_status ?? null,
  }));
}
