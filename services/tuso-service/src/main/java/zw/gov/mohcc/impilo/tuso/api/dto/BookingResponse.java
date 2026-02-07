package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID resourceId,
        String resourceName,
        Long facilityId,
        String bookedBy,
        String subjectRef,
        String purpose,
        Instant startTime,
        Instant endTime,
        String status,
        String notes,
        String cancelledBy,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {}
