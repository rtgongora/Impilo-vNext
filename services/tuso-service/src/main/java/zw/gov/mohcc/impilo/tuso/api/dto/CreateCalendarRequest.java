package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateCalendarRequest(
        @NotNull(message = "Day of week is required")
        @Min(value = 1, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
        @Max(value = 7, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
        Integer dayOfWeek,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @Min(value = 5, message = "Slot duration must be at least 5 minutes")
        @Max(value = 480, message = "Slot duration must not exceed 480 minutes")
        int slotDurationMinutes,

        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        LocalDate effectiveTo
) {
    public CreateCalendarRequest {
        if (slotDurationMinutes == 0) {
            slotDurationMinutes = 30;
        }
    }
}
