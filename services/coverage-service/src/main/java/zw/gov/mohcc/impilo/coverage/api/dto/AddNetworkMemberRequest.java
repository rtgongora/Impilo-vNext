package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record AddNetworkMemberRequest(
        @NotBlank String providerId,
        UUID contractId
) {
}
