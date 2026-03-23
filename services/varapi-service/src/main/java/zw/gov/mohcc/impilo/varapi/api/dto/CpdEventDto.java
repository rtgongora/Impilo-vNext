package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CpdEventDto(
        Long id,
        Long cycleId,
        String eventType,
        String title,
        String description,
        int pointsAwarded,
        LocalDate eventDate,
        String externalRef,
        boolean verified,
        String verifiedBy,
        Instant verifiedAt,
        Instant createdAt
) {}
