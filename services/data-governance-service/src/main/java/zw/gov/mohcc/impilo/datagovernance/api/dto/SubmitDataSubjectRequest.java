package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Submit a data-subject rights request (account deletion / erasure).
 * {@code requestType} defaults to ACCOUNT_DELETION when omitted.
 */
public record SubmitDataSubjectRequest(
        @JsonProperty("subjectId") @NotBlank String subjectId,
        @JsonProperty("requestType") String requestType,
        @JsonProperty("reason") String reason
) {}
