package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.TemperatureSource;
import zw.gov.mohcc.impilo.inventory.persistence.entity.TemperatureLogEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a cold-chain temperature log.
 */
public record TemperatureLogDto(
        UUID logId,
        UUID ccLocationId,
        double temperature,
        TemperatureSource source,
        boolean withinRange,
        String recordedBy,
        OffsetDateTime recordedAt
) {
    public static TemperatureLogDto from(TemperatureLogEntity e) {
        return new TemperatureLogDto(
                e.getLogId(), e.getCcLocationId(), e.getTemperature(), e.getSource(),
                e.isWithinRange(), e.getRecordedBy(), e.getRecordedAt());
    }
}
