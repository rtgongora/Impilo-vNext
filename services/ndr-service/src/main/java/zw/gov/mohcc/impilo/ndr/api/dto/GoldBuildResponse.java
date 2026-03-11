package zw.gov.mohcc.impilo.ndr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoldBuildResponse(
        String status,
        @JsonProperty("encounters_created") int encountersCreated,
        @JsonProperty("encounters_updated") int encountersUpdated,
        @JsonProperty("build_id") String buildId,
        @JsonProperty("built_at") String builtAt
) {}
