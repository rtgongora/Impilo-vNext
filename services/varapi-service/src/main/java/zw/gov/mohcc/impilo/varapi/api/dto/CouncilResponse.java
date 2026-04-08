package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;

public record CouncilResponse(
        Long id,
        String councilCode,
        String name,
        String councilType,
        String description,
        String status,
        String website,
        String email,
        String phone,
        Instant createdAt,
        Instant updatedAt
) {}
