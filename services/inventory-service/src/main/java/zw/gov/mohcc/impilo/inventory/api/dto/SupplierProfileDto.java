package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.SupplierType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierProfileEntity;

import java.util.UUID;

/** DTO for a supplier profile. */
public record SupplierProfileDto(
        UUID supplierId,
        String name,
        SupplierType type,
        String licenceNumber,
        String contact,
        boolean active
) {
    public static SupplierProfileDto from(SupplierProfileEntity e) {
        return new SupplierProfileDto(
                e.getSupplierId(), e.getName(), e.getType(), e.getLicenceNumber(), e.getContact(), e.isActive());
    }
}
