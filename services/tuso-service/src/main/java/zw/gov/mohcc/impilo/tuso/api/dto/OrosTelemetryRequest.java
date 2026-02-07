package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrosTelemetryRequest(
        @NotNull(message = "Facility ID is required")
        Long facilityId,

        @NotEmpty(message = "At least one metric entry is required")
        @Valid
        List<MetricEntry> metrics
) {}
