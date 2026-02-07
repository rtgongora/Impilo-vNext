package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ResourceResponse(
        UUID id,
        Long facilityId,
        String name,
        String resourceType,
        int capacity,
        String status,
        boolean maintenance,
        String locationDesc,
        Map<String, Object> metadata,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
