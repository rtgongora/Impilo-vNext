package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DecideResponse(
        String decision,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("reason_codes") List<String> reasonCodes,
        @JsonProperty("ttl_seconds") int ttlSeconds
) {}
