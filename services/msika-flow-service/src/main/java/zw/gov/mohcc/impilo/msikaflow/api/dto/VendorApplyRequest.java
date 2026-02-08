package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VendorApplyRequest(
        @NotBlank String name,
        @NotBlank String type,
        String coverage
) {}
