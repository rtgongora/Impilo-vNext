package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Reason payload for approve / reject / revoke transitions on a waiver. */
public record WaiverDecisionRequest(
        @JsonProperty("reason") String reason
) {}
