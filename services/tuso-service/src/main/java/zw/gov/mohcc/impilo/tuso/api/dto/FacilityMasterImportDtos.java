package zw.gov.mohcc.impilo.tuso.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class FacilityMasterImportDtos {

    private FacilityMasterImportDtos() {}

    public record MasterFacilitySeedRecord(
            @NotBlank @JsonProperty("facility_uid") String facilityUid,
            @JsonProperty("facility_code") String facilityCode,
            @NotBlank @JsonProperty("facility_name") String facilityName,
            String province,
            String district,
            @JsonProperty("facility_type") String facilityType,
            String ownership,
            @JsonProperty("location_context") String locationContext,
            @JsonProperty("service_level") String serviceLevel,
            String status,
            @JsonProperty("bed_capacity") Integer bedCapacity,
            Double latitude,
            Double longitude,
            @JsonProperty("contact_phone_e164") String contactPhoneE164,
            @JsonProperty("source_dataset_date") String sourceDatasetDate,
            /**
             * Raw source values preserved verbatim (never lost during canonicalisation). Populated by the
             * CSV loader from the {@code *_raw} columns; null on the JSON-seed path. Not part of the wire
             * contract — carried through to row-level staging.
             */
            @JsonProperty("raw_values") Map<String, Object> rawValues
    ) {
        /** Backward-compatible 15-arg constructor (no raw values) for the JSON seed + existing callers. */
        public MasterFacilitySeedRecord(
                String facilityUid, String facilityCode, String facilityName, String province,
                String district, String facilityType, String ownership, String locationContext,
                String serviceLevel, String status, Integer bedCapacity, Double latitude,
                Double longitude, String contactPhoneE164, String sourceDatasetDate) {
            this(facilityUid, facilityCode, facilityName, province, district, facilityType, ownership,
                    locationContext, serviceLevel, status, bedCapacity, latitude, longitude,
                    contactPhoneE164, sourceDatasetDate, null);
        }
    }

    public record FacilityMasterImportRequest(
            boolean dryRun,
            boolean syncNdila,
            boolean reconcileDuplicateCodes,
            @Valid List<MasterFacilitySeedRecord> records
    ) {}

    public record FacilityMasterImportRowResult(
            String facilityUid,
            String facilityCode,
            String facilityName,
            String outcome,
            String qualityFlag,
            String message,
            Long facilityId
    ) {}

    public record FacilityMasterImportResponse(
            boolean dryRun,
            int recordsTotal,
            int recordsCreated,
            int recordsUpdated,
            int recordsSkipped,
            int recordsFailed,
            int warningsCount,
            List<FacilityMasterImportRowResult> results,
            Map<String, Object> qualitySummary
    ) {}
}
