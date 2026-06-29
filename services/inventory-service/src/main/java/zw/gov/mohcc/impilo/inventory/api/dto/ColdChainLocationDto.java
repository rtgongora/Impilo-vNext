package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.ColdChainLocationType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainLocationEntity;

import java.util.UUID;

/**
 * DTO for a Dura cold-chain location.
 */
public record ColdChainLocationDto(
        UUID ccLocationId,
        UUID facilityId,
        UUID storeId,
        String name,
        ColdChainLocationType type,
        double tempMin,
        double tempMax,
        boolean active
) {
    public static ColdChainLocationDto from(ColdChainLocationEntity e) {
        return new ColdChainLocationDto(
                e.getCcLocationId(), e.getFacilityId(), e.getStoreId(), e.getName(),
                e.getType(), e.getTempMin(), e.getTempMax(), e.isActive());
    }
}
