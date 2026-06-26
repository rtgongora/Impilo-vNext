package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages facility service-points (TUSO SoR). Created by the facility-mode setup
 * wizard; each mutation is audited via the outbox.
 */
@Service
public class ServicePointService {

    private static final Logger log = LoggerFactory.getLogger(ServicePointService.class);

    private final ServicePointRepository repository;
    private final FacilityRepository facilityRepository;
    private final EventOutboxRepository outboxRepository;

    public ServicePointService(ServicePointRepository repository,
                               FacilityRepository facilityRepository,
                               EventOutboxRepository outboxRepository) {
        this.repository = repository;
        this.facilityRepository = facilityRepository;
        this.outboxRepository = outboxRepository;
    }

    public record CreateServicePointRequest(
            String name, String code, String servicePointType,
            Long facilityUnitId, String queueId, String workflowArchetype,
            Map<String, Object> metadata) {}

    @Transactional(readOnly = true)
    public List<ServicePointEntity> list(Long facilityId) {
        return repository.findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(facilityId);
    }

    @Transactional
    public ServicePointEntity create(TrustContext ctx, Long facilityId, CreateServicePointRequest req) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        ServicePointEntity sp = new ServicePointEntity();
        sp.setFacilityId(facilityId);
        sp.setTenantId(facility.getTenantId());
        sp.setName(req.name());
        sp.setCode(req.code());
        if (req.servicePointType() != null && !req.servicePointType().isBlank()) {
            sp.setServicePointType(req.servicePointType());
        }
        sp.setFacilityUnitId(req.facilityUnitId());
        sp.setQueueId(req.queueId());
        sp.setWorkflowArchetype(req.workflowArchetype());
        sp.setMetadata(req.metadata());
        sp.setCreatedBy(actor(ctx));
        sp.setUpdatedBy(actor(ctx));
        ServicePointEntity saved = repository.save(sp);

        publishEvent(facilityId, saved.getId(), "SERVICE_POINT_CREATED");
        log.info("Service point {} created for facility {} (actor={})", saved.getId(), facilityId, actor(ctx));
        return saved;
    }

    @Transactional
    public ServicePointEntity deactivate(TrustContext ctx, UUID servicePointId) {
        ServicePointEntity sp = repository.findById(servicePointId)
                .orElseThrow(() -> new IllegalArgumentException("Service point not found: " + servicePointId));
        sp.setActive(false);
        sp.setStatus("RETIRED");
        sp.setUpdatedBy(actor(ctx));
        ServicePointEntity saved = repository.save(sp);
        publishEvent(sp.getFacilityId(), servicePointId, "SERVICE_POINT_RETIRED");
        return saved;
    }

    private void publishEvent(Long facilityId, UUID servicePointId, String eventType) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("SERVICE_POINT");
        event.setAggregateId(servicePointId.toString());
        event.setEventType(eventType);
        event.setPayload(String.format("{\"facilityId\":%d,\"servicePointId\":\"%s\"}",
                facilityId, servicePointId));
        outboxRepository.save(event);
    }

    private static String actor(TrustContext ctx) {
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
