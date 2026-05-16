package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record StatusChangeRequest(
        @JsonProperty("reason") String reason,
        @JsonProperty("metadata") Map<String, Object> metadata
) {}
