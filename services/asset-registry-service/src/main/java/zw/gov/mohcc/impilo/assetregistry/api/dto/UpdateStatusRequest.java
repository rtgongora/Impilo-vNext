package zw.gov.mohcc.impilo.assetregistry.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(
        @NotBlank String status
) {}
