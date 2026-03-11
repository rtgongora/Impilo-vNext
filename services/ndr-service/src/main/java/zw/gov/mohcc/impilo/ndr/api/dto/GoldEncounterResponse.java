package zw.gov.mohcc.impilo.ndr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GoldEncounterResponse(
        List<EncounterItem> items,
        String cursor,
        int limit,
        @JsonProperty("has_more") boolean hasMore
) {

    public record EncounterItem(
            @JsonProperty("encounter_id") String encounterId,
            @JsonProperty("patient_ref") String patientRef,
            @JsonProperty("facility_ref") String facilityRef,
            @JsonProperty("started_at") String startedAt,
            String status,
            @JsonProperty("source_event_id") String sourceEventId,
            @JsonProperty("updated_at") String updatedAt
    ) {}
}
