package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.CapabilityEntity;

import java.util.UUID;

/**
 * DTO for capability configuration in API responses.
 */
public record CapabilityDto(
        UUID capabilityId,
        UUID tenantId,
        UUID facilityId,
        OrderType orderType,
        AdapterMode adapterMode,
        String externalEndpoint,
        String config,
        boolean active
) {
    /**
     * Factory method to create a DTO from a capability entity.
     */
    public static CapabilityDto from(CapabilityEntity entity) {
        return new CapabilityDto(
                entity.getCapabilityId(),
                entity.getTenantId(),
                entity.getFacilityId(),
                entity.getOrderType(),
                entity.getAdapterMode(),
                entity.getExternalEndpoint(),
                entity.getConfig(),
                entity.isActive()
        );
    }
}
