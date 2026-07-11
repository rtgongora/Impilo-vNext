package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;

import java.util.List;
import java.util.Map;

/**
 * Facility unit (department) administration (TUSO SoR). Exposes the existing
 * {@code FacilityUnitEntity} for the facility-mode setup wizard and admin UI.
 *
 * <p>Units that {@code registrationRequired} are created in {@code DRAFT} regulatory
 * status — they only become {@code REGISTERED_ACTIVE} through the governed
 * {@code FacilityRegulatoryService} application flow (no shortcut to registered here).</p>
 */
@Service
public class FacilityUnitService {

    private static final Logger log = LoggerFactory.getLogger(FacilityUnitService.class);

    private final FacilityUnitRepository unitRepository;
    private final FacilityRepository facilityRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilityUnitService(FacilityUnitRepository unitRepository,
                               FacilityRepository facilityRepository,
                               EventOutboxRepository outboxRepository) {
        this.unitRepository = unitRepository;
        this.facilityRepository = facilityRepository;
        this.outboxRepository = outboxRepository;
    }

    public record CreateUnitRequest(
            String name, String unitType, String serviceLine,
            boolean registrationRequired, Map<String, Object> metadata) {}

    @Transactional(readOnly = true)
    public List<FacilityUnitEntity> list(Long facilityId) {
        return unitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId);
    }

    @Transactional
    public FacilityUnitEntity create(TrustContext ctx, Long facilityId, CreateUnitRequest req) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        if (ctx != null && !facility.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }

        FacilityUnitEntity unit = new FacilityUnitEntity();
        unit.setFacility(facility);
        unit.setName(req.name());
        unit.setUnitType(req.unitType() != null ? req.unitType() : "DEPARTMENT");
        unit.setServiceLine(req.serviceLine());
        unit.setRegistrationRequired(req.registrationRequired());
        // Honest default: a registration-required unit starts DRAFT; only the governed
        // regulatory application can move it to REGISTERED_ACTIVE.
        unit.setRegulatoryStatus(FacilityRegulatoryStatus.DRAFT);
        unit.setMetadata(req.metadata());
        unit.setCreatedBy(actor(ctx));
        unit.setUpdatedBy(actor(ctx));
        FacilityUnitEntity saved = unitRepository.save(unit);

        publishEvent(facilityId, saved.getId(), "FACILITY_UNIT_CREATED");
        log.info("Facility unit {} created for facility {} (actor={})", saved.getId(), facilityId, actor(ctx));
        return saved;
    }

    private void publishEvent(Long facilityId, Long unitId, String eventType) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY_UNIT");
        event.setAggregateId(String.valueOf(unitId));
        event.setEventType(eventType);
        event.setPayload(String.format("{\"facilityId\":%d,\"unitId\":%d}", facilityId, unitId));
        // Mandatory outbox hygiene — null pod_id/idempotency_key poisons the publisher drain.
        event.setPodId("national-spine");
        event.setIdempotencyKey("tuso:facility-unit:" + unitId + ":" + eventType + ":" + java.util.UUID.randomUUID());
        outboxRepository.save(event);
    }

    public record UpdateUnitRequest(
            String name, String unitType, String serviceLine, Map<String, Object> metadata) {}

    /** Partial update; null fields are left unchanged. Regulatory status is never writable here. */
    @Transactional
    public FacilityUnitEntity update(TrustContext ctx, Long facilityId, Long unitId, UpdateUnitRequest req) {
        FacilityUnitEntity unit = requireUnit(ctx, facilityId, unitId);
        if (req.name() != null && !req.name().isBlank()) {
            unit.setName(req.name());
        }
        if (req.unitType() != null && !req.unitType().isBlank()) {
            unit.setUnitType(req.unitType());
        }
        if (req.serviceLine() != null) {
            unit.setServiceLine(req.serviceLine());
        }
        if (req.metadata() != null) {
            unit.setMetadata(req.metadata());
        }
        unit.setUpdatedBy(actor(ctx));
        FacilityUnitEntity saved = unitRepository.save(unit);
        publishEvent(facilityId, unitId, "FACILITY_UNIT_UPDATED");
        log.info("Facility unit {} updated for facility {} (actor={})", unitId, facilityId, actor(ctx));
        return saved;
    }

    /**
     * Administrative retirement — the unit stops offering services. Distinct from
     * governed enforcement closure ({@code CLOSED}), which only the regulatory flow applies.
     */
    @Transactional
    public FacilityUnitEntity retire(TrustContext ctx, Long facilityId, Long unitId) {
        FacilityUnitEntity unit = requireUnit(ctx, facilityId, unitId);
        if (unit.getRegulatoryStatus() == FacilityRegulatoryStatus.CLOSED
                || unit.getRegulatoryStatus() == FacilityRegulatoryStatus.VOLUNTARILY_CLOSED) {
            return unit;
        }
        unit.setRegulatoryStatus(FacilityRegulatoryStatus.VOLUNTARILY_CLOSED);
        unit.setUpdatedBy(actor(ctx));
        FacilityUnitEntity saved = unitRepository.save(unit);
        publishEvent(facilityId, unitId, "FACILITY_UNIT_RETIRED");
        log.info("Facility unit {} retired for facility {} (actor={})", unitId, facilityId, actor(ctx));
        return saved;
    }

    private FacilityUnitEntity requireUnit(TrustContext ctx, Long facilityId, Long unitId) {
        FacilityUnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Facility unit not found: " + unitId));
        if (unit.getFacility() == null || !unit.getFacility().getId().equals(facilityId)) {
            throw new IllegalArgumentException("Facility unit " + unitId + " does not belong to facility " + facilityId);
        }
        if (ctx != null && !unit.getFacility().getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }
        return unit;
    }

    private static String actor(TrustContext ctx) {
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
