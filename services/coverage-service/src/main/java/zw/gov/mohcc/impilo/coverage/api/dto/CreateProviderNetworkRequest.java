package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProviderNetworkRequest(
        @NotBlank String name,
        @NotBlank String payerId,
        String networkType,
        String serviceArea
) {
}
