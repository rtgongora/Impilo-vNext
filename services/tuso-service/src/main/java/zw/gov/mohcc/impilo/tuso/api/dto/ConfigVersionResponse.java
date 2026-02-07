package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.Map;

public record ConfigVersionResponse(
        int version,
        Map<String, Object> configData,
        String createdBy,
        Instant createdAt,
        Integer rollbackFrom
) {}
