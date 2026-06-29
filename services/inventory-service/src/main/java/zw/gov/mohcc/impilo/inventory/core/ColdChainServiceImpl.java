package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.*;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainExcursionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainLocationEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.TemperatureLogEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ColdChainExcursionRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ColdChainLocationRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.TemperatureLogRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura cold-chain service.
 */
@Service
public class ColdChainServiceImpl implements ColdChainService {

    private static final Logger log = LoggerFactory.getLogger(ColdChainServiceImpl.class);

    private final ColdChainLocationRepository locationRepository;
    private final TemperatureLogRepository logRepository;
    private final ColdChainExcursionRepository excursionRepository;

    public ColdChainServiceImpl(ColdChainLocationRepository locationRepository,
                                TemperatureLogRepository logRepository,
                                ColdChainExcursionRepository excursionRepository) {
        this.locationRepository = locationRepository;
        this.logRepository = logRepository;
        this.excursionRepository = excursionRepository;
    }

    @Override
    @Transactional
    public ColdChainLocationEntity registerLocation(UUID facilityId, UUID storeId, String name,
                                                    ColdChainLocationType type, double tempMin, double tempMax) {
        TrustContext ctx = TrustContextHolder.require();
        if (facilityId == null) {
            throw new IllegalArgumentException("facilityId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (tempMin > tempMax) {
            throw new IllegalArgumentException("tempMin must not exceed tempMax");
        }

        ColdChainLocationEntity location = new ColdChainLocationEntity();
        location.setTenantId(ctx.tenantId());
        location.setFacilityId(facilityId);
        location.setStoreId(storeId);
        location.setName(name);
        location.setType(type != null ? type : ColdChainLocationType.FRIDGE);
        location.setTempMin(tempMin);
        location.setTempMax(tempMax);

        location = locationRepository.save(location);
        log.info("Cold-chain location registered: name={}, type={}, range=[{},{}]",
                name, location.getType(), tempMin, tempMax);
        return location;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColdChainLocationEntity> listLocations(UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        if (facilityId != null) {
            return locationRepository.findByTenantIdAndFacilityIdOrderByName(ctx.tenantId(), facilityId);
        }
        return locationRepository.findByTenantIdOrderByName(ctx.tenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public ColdChainLocationEntity getLocation(UUID ccLocationId) {
        return loadOwnedLocation(ccLocationId);
    }

    @Override
    @Transactional
    public LogResult logTemperature(UUID ccLocationId, double temperature, TemperatureSource source,
                                    OffsetDateTime recordedAt) {
        TrustContext ctx = TrustContextHolder.require();
        ColdChainLocationEntity location = loadOwnedLocation(ccLocationId);

        boolean withinRange = temperature >= location.getTempMin() && temperature <= location.getTempMax();

        TemperatureLogEntity tlog = new TemperatureLogEntity();
        tlog.setTenantId(ctx.tenantId());
        tlog.setCcLocationId(ccLocationId);
        tlog.setTemperature(temperature);
        tlog.setSource(source != null ? source : TemperatureSource.MANUAL);
        tlog.setWithinRange(withinRange);
        tlog.setRecordedBy(ctx.actorId());
        tlog.setRecordedAt(recordedAt);
        tlog = logRepository.save(tlog);

        ColdChainExcursionEntity excursion = null;
        if (!withinRange) {
            excursion = new ColdChainExcursionEntity();
            excursion.setTenantId(ctx.tenantId());
            excursion.setCcLocationId(ccLocationId);
            excursion.setLogId(tlog.getLogId());
            excursion.setTemperature(temperature);
            excursion.setBreach(temperature < location.getTempMin() ? ExcursionBreach.LOW : ExcursionBreach.HIGH);
            excursion.setStatus(ExcursionStatus.OPEN);
            excursion = excursionRepository.save(excursion);
            log.warn("Cold-chain excursion: location={}, temp={}, breach={}",
                    ccLocationId, temperature, excursion.getBreach());
        }

        return new LogResult(tlog, excursion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemperatureLogEntity> recentLogs(UUID ccLocationId, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        loadOwnedLocation(ccLocationId);
        int capped = Math.max(1, Math.min(limit, 500));
        return logRepository.findByTenantIdAndCcLocationIdOrderByRecordedAtDesc(
                ctx.tenantId(), ccLocationId, PageRequest.of(0, capped)).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColdChainExcursionEntity> listExcursions(ExcursionStatus status) {
        TrustContext ctx = TrustContextHolder.require();
        if (status != null) {
            return excursionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(ctx.tenantId(), status);
        }
        return excursionRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
    }

    @Override
    @Transactional
    public ColdChainExcursionEntity resolveExcursion(UUID excursionId, ExcursionResolution resolution, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        if (resolution == null) {
            throw new IllegalArgumentException("resolution is required");
        }
        ColdChainExcursionEntity excursion = excursionRepository.findById(excursionId)
                .orElseThrow(() -> new IllegalArgumentException("Excursion not found: " + excursionId));
        if (excursion.getTenantId() == null || !excursion.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Excursion does not belong to the current tenant");
        }
        if (excursion.getStatus() == ExcursionStatus.RESOLVED) {
            throw new IllegalStateException("Excursion already resolved: " + excursionId);
        }
        excursion.setStatus(ExcursionStatus.RESOLVED);
        excursion.setResolution(resolution);
        excursion.setNotes(notes);
        excursion.setResolvedBy(ctx.actorId());
        excursion.setResolvedAt(OffsetDateTime.now());
        excursion = excursionRepository.save(excursion);
        log.info("Cold-chain excursion resolved: excursionId={}, resolution={}", excursionId, resolution);
        return excursion;
    }

    private ColdChainLocationEntity loadOwnedLocation(UUID ccLocationId) {
        TrustContext ctx = TrustContextHolder.require();
        ColdChainLocationEntity location = locationRepository.findById(ccLocationId)
                .orElseThrow(() -> new IllegalArgumentException("Cold-chain location not found: " + ccLocationId));
        if (location.getTenantId() == null || !location.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Cold-chain location does not belong to the current tenant");
        }
        return location;
    }
}
