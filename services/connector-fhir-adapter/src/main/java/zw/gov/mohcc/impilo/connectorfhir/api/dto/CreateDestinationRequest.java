package zw.gov.mohcc.impilo.connectorfhir.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDestinationRequest(
        @NotBlank String name,
        @NotBlank String endpointUrl,
        String resourceTypes,
        String authType) {}
