package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewAppealRequest(
        @NotBlank String reviewerId
) {}
