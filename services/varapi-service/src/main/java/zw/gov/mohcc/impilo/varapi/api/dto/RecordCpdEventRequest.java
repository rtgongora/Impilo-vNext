package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record RecordCpdEventRequest(
        String eventType,
        String title,
        String description,
        int pointsAwarded,
        LocalDate eventDate,
        String externalRef
) {}
