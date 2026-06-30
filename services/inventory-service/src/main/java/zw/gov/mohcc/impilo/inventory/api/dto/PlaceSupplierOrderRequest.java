package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request to place a customer order with a supplier. */
public record PlaceSupplierOrderRequest(
        UUID buyerFacilityId,
        String currency,
        @NotEmpty List<Line> lines,
        String notes
) {
    public record Line(
            String itemCode,
            @NotBlank String itemName,
            @Positive int qty,
            BigDecimal unitPrice
    ) {}
}
