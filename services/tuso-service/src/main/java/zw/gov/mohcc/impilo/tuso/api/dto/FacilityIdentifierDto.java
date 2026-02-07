package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FacilityIdentifierDto(
        @NotBlank(message = "Identifier system is required")
        String system,

        @NotBlank(message = "Identifier value is required")
        String value
) {}
