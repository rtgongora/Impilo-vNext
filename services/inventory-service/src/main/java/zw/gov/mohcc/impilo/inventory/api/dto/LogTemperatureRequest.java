package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.TemperatureSource;

import java.time.OffsetDateTime;

/**
 * Request to record a temperature reading for a cold-chain location.
 */
public record LogTemperatureRequest(
        double temperature,
        TemperatureSource source,
        OffsetDateTime recordedAt
) {}
