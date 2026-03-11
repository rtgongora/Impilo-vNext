package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SnapshotResponse<T>(
        List<T> items,
        @JsonProperty("next_cursor") String nextCursor,
        int limit,
        @JsonProperty("has_next") boolean hasNext
) {}
