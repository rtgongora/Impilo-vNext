package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create-payer request (spec §8). Only code + legal name are mandatory this wave.
 */
public record CreatePayerRequest(
        @NotBlank String payerCode,
        @NotBlank String legalName,
        String tradingName,
        String organisationType,
        String integrationMethod,
        String settlementReference,
        String regulatoryStatus,
        String sourceAuthority
) {}
