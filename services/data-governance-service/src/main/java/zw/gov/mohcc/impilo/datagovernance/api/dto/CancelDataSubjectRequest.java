package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Cancel a pending data-subject rights request for a subject. */
public record CancelDataSubjectRequest(
        @JsonProperty("subjectId") @NotBlank String subjectId,
        @JsonProperty("requestType") String requestType
) {}
