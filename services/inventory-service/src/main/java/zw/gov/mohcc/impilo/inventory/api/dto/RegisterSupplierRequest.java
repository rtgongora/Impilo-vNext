package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.inventory.domain.SupplierType;

/** Request to register a supplier. */
public record RegisterSupplierRequest(
        @NotBlank String name,
        SupplierType type,
        String licenceNumber,
        String contact
) {}
