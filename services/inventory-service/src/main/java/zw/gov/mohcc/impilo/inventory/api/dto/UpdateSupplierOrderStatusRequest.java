package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.inventory.domain.SupplierOrderStatus;

/** Request to update a supplier order's status. */
public record UpdateSupplierOrderStatusRequest(
        @NotNull SupplierOrderStatus status
) {}
