package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CpdEventDto(
        Long id,
        String eventType,
        String title,
        int pointsAwarded,
        LocalDate eventDate,
        boolean verified,
        Instant verifiedAt
) {}
