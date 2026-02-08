package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record LineItemRequest(
        @NotBlank String msikaCoreCode,
        String kind,
        @Min(1) int qty,
        BigDecimal unitPrice,
        String fulfillmentMode,
        String substitutionPolicy,
        String metadata
) {}
