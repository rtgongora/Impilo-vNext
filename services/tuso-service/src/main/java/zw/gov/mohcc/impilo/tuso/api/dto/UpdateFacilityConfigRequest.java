package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateFacilityConfigRequest(
        @NotNull(message = "Config data is required")
        Map<String, Object> configData
) {}
