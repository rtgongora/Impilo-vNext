package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingRequest(
        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        String subjectRef,
        String purpose,
        String notes
) {}
