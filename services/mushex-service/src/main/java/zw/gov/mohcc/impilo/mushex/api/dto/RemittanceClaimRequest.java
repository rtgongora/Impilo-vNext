package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RemittanceClaimRequest(
        @NotBlank String token,
        @NotBlank String otp
) {}
