package zw.gov.mohcc.impilo.search.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record IndexRequest(
        @NotBlank String entityType,
        @NotBlank String entityId,
        @NotNull Map<String, Object> contentJson,
        List<String> tags,
        String sourceEvent
) {}
