package zw.gov.mohcc.impilo.costa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateChargeRecordRequest(
        @NotBlank String chargeCode,
        @NotBlank String chargeType,
        @NotBlank String sourceType,
        @NotBlank String sourceRef,
        String clientRef,
        String payerRef,
        String providerRef,
        String facilityId,
        String itemRef,
        String offeringRef,
        String serviceRef,
        String billId,
        String lineId,
        @NotNull BigDecimal chargeAmount,
        String currency,
        Boolean billableFlag,
        String metadataJson) {
}
