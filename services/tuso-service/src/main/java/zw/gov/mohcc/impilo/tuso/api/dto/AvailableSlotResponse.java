package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AvailableSlotResponse(
        List<Slot> slots
) {
    public record Slot(
            Instant startTime,
            Instant endTime,
            UUID resourceId,
            String resourceName
    ) {}
}
