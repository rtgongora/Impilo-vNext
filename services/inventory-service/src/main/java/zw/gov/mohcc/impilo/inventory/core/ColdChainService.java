package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.ColdChainLocationType;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionResolution;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionStatus;
import zw.gov.mohcc.impilo.inventory.domain.TemperatureSource;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainExcursionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainLocationEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.TemperatureLogEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dura cold-chain service: cold-chain locations, temperature logging with
 * automatic excursion detection, and excursion resolution.
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface ColdChainService {

    ColdChainLocationEntity registerLocation(UUID facilityId, UUID storeId, String name,
                                             ColdChainLocationType type, double tempMin, double tempMax);

    List<ColdChainLocationEntity> listLocations(UUID facilityId);

    ColdChainLocationEntity getLocation(UUID ccLocationId);

    /**
     * Record a temperature reading. If it falls outside the location's safe
     * range, an OPEN excursion is raised automatically.
     */
    LogResult logTemperature(UUID ccLocationId, double temperature, TemperatureSource source,
                             OffsetDateTime recordedAt);

    /**
     * Recent temperature logs for a location, newest first.
     */
    List<TemperatureLogEntity> recentLogs(UUID ccLocationId, int limit);

    List<ColdChainExcursionEntity> listExcursions(ExcursionStatus status);

    /**
     * Resolve an open excursion as RELEASED (stock safe) or WASTAGE.
     */
    ColdChainExcursionEntity resolveExcursion(UUID excursionId, ExcursionResolution resolution, String notes);

    /**
     * Result of logging a temperature: the log, and the excursion raised
     * ({@code null} when the reading was within range).
     */
    record LogResult(TemperatureLogEntity log, ColdChainExcursionEntity excursion) {}
}
