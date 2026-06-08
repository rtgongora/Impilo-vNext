import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface FacilityQualityReportSummary {
  source_file?: string;
  source_dataset_date?: string;
  records_total?: number;
  records_open?: number;
  records_with_valid_coordinates?: number;
  records_missing_or_invalid_coordinates?: number;
  records_missing_facility_code?: number;
  duplicate_facility_codes_count?: number;
}

export interface FacilityQualityReport {
  status?: string;
  message?: string;
  summary?: FacilityQualityReportSummary;
}

export interface FacilityDataQualitySummary {
  qualityReport: FacilityQualityReport;
  geocodeReviewPending: number;
  geocodeReviewQueue: unknown[];
  geocodeReviewError?: string;
}

export function useFacilityDataQuality() {
  return useQuery<ApiResponse<FacilityDataQualitySummary>>({
    queryKey: ["facilities", "data-quality", "summary"],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityDataQualitySummary>>("/internal/v1/facilities/data-quality/summary"),
    staleTime: 60_000,
  });
}
