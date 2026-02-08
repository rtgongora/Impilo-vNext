package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimPickupRequest(
        @NotBlank String tokenOrOtp
) {}
