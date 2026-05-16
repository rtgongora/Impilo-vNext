package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ChainOfCustodyRequest(
        @NotBlank @JsonProperty("event_kind") String eventKind,
        @JsonProperty("package_id") UUID packageId,
        @JsonProperty("custody_holder") String custodyHolder,
        @JsonProperty("custody_location") String custodyLocation,
        @JsonProperty("seal_id") String sealId,
        @JsonProperty("temperature_c") BigDecimal temperatureC,
        @JsonProperty("handover_notes") String handoverNotes,
        @JsonProperty("verification") String verification,
        @JsonProperty("device_used") String deviceUsed,
        @JsonProperty("evidence_uri") String evidenceUri,
        @JsonProperty("exception_flags") List<String> exceptionFlags
) {}
