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
            @JsonProperty("source_dataset_date") String sourceDatasetDate
    ) {}

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
