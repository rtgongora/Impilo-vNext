import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Governed facility profile-completeness lane (TUSO SoR, composed by the BFF
 * FacilityModeController). Backs the facility-mode cockpit prompt asking the
 * facility administrator / practitioner-in-charge to complete missing governed
 * fields — especially geocodes.
 */

interface BffEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

/** Missing-field keys — aligned with TUSO's import-plane acceptable-missing vocabulary. */
export type FacilityMissingField =
  | "missing_latitude"
  | "missing_longitude"
  | "missing_facility_type"
  | "missing_ownership"
  | "missing_operating_status"
  | "missing_address_line1"
  | "missing_city"
  | "missing_ward";

export interface FacilityCompleteness {
  facilityId: number;
  facilityUuid?: string | null;
  complete: boolean;
  missingFields: FacilityMissingField[];
  geocodeStatus: "PRESENT" | "MISSING";
}

export type GeocodeCaptureSource = "DEVICE_GPS" | "ADDRESS_SEARCH" | "MANUAL";

export interface GeocodeProvenance {
  source: GeocodeCaptureSource;
  provider?: string;
  confidence?: number;
  accuracyMeters?: number;
}

/** Governed completion payload — omitted fields are left unchanged (TUSO enforces authz). */
export interface CompleteFacilityProfileInput {
  facilityType?: string;
  ownership?: string;
  operationalStatus?: string;
  latitude?: number;
  longitude?: number;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  province?: string;
  district?: string;
  ward?: string;
  postalCode?: string;
  geocodeProvenance?: GeocodeProvenance;
}

export function facilityCompletenessQueryKey(facilityId: string | number) {
  return ["facility-completeness", String(facilityId)] as const;
}

export function useFacilityCompleteness(facilityId: string | number) {
  return useQuery({
    queryKey: facilityCompletenessQueryKey(facilityId),
    enabled: facilityId != null && facilityId !== "",
    staleTime: 60_000,
    queryFn: async () => {
      const resp = await apiClient.get<BffEnvelope<FacilityCompleteness>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/completeness`,
      );
      return resp.data;
    },
  });
}

export function useCompleteFacilityProfile(facilityId: string | number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: CompleteFacilityProfileInput) => {
      const resp = await apiClient.post<BffEnvelope<FacilityCompleteness>>(
        `/internal/v1/facility-mode/${encodeURIComponent(String(facilityId))}/complete-profile`,
        input,
      );
      return resp.data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: facilityCompletenessQueryKey(facilityId) });
      qc.invalidateQueries({ queryKey: ["facility-mode-context", String(facilityId)] });
    },
  });
}

/** Human-readable labels for the governed missing-field keys. */
export const MISSING_FIELD_LABELS: Record<FacilityMissingField, string> = {
  missing_latitude: "Latitude (geocode)",
  missing_longitude: "Longitude (geocode)",
  missing_facility_type: "Facility type",
  missing_ownership: "Ownership",
  missing_operating_status: "Operating status",
  missing_address_line1: "Street address",
  missing_city: "City / town",
  missing_ward: "Ward",
};
