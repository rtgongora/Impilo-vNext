package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.ExcursionBreach;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionResolution;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainExcursionEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a cold-chain excursion.
 */
public record ColdChainExcursionDto(
        UUID excursionId,
        UUID ccLocationId,
        UUID logId,
        double temperature,
        ExcursionBreach breach,
        ExcursionStatus status,
        ExcursionResolution resolution,
        String notes,
        String resolvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
    public static ColdChainExcursionDto from(ColdChainExcursionEntity e) {
        return new ColdChainExcursionDto(
                e.getExcursionId(), e.getCcLocationId(), e.getLogId(), e.getTemperature(),
                e.getBreach(), e.getStatus(), e.getResolution(), e.getNotes(),
                e.getResolvedBy(), e.getCreatedAt(), e.getResolvedAt());
    }
}
