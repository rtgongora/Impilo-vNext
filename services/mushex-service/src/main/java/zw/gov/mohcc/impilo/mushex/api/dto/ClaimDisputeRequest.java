package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimDisputeRequest(
        @NotBlank String reason
) {}
