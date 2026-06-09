package zw.gov.mohcc.impilo.inpatient.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record StartWardRoundRequest(
        @JsonProperty("admissionRef")
        @JsonAlias("admission_ref") UUID admissionRef,
        @JsonProperty("wardId")
        @JsonAlias("ward_id") UUID wardId,
        @JsonProperty("roundType")
        @JsonAlias("round_type") String roundType,
        @JsonProperty("ledBy")
        @JsonAlias("led_by") String ledBy,
        String notes
) {}
