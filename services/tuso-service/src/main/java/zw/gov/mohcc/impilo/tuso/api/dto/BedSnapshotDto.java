package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BedSnapshotDto(
        String ward,

        @NotNull(message = "Total beds is required")
        @Min(value = 0, message = "Total beds cannot be negative")
        Integer totalBeds,

        @NotNull(message = "Occupied beds is required")
        @Min(value = 0, message = "Occupied beds cannot be negative")
        Integer occupiedBeds
) {}
