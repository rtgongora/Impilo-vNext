package zw.gov.mohcc.impilo.tshepo.audit.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AccessHistoryEntry;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventQueryParams;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventResponse;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditEventRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Paginated query service for audit events.
 * Supports filtering by tenant, actor, subject, time range, and event type.
 * All queries are tenant-isolated.
 */
@Service
public class AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryService.class);

    private final AuditEventRepository eventRepository;

    public AuditQueryService(AuditEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Query audit events with multiple filter criteria.
     * All results are scoped to the specified tenant.
     */
    @Transactional(readOnly = true)
    public Page<AuditEventResponse> queryEvents(AuditEventQueryParams params) {
        Pageable pageable = PageRequest.of(params.page(), params.size(),
                Sort.by(Sort.Direction.DESC, "sequenceNumber"));

        Specification<AuditEventEntity> spec = buildSpecification(params);

        Page<AuditEventEntity> entities = eventRepository.findAll(spec, pageable);
        return entities.map(this::toResponse);
    }

    /**
     * Find a single audit event by ID, scoped to the given tenant.
     */
    @Transactional(readOnly = true)
    public Optional<AuditEventResponse> findEventById(UUID tenantId, UUID eventId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .map(this::toResponse);
    }

    /**
     * Patient-visible access history for a given subject reference.
     * Returns redacted entries: no internal IDs, no detail payloads.
     */
    @Transactional(readOnly = true)
    public Page<AccessHistoryEntry> getAccessHistory(UUID tenantId, String subjectRef,
                                                      int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditEventEntity> entities = eventRepository
                .findByTenantIdAndSubjectRef(tenantId, subjectRef, pageable);

        return entities.map(this::toAccessHistoryEntry);
    }

    /**
     * Build a JPA Specification from query parameters.
     * Tenant isolation is always enforced.
     */
    private Specification<AuditEventEntity> buildSpecification(AuditEventQueryParams params) {
        Specification<AuditEventEntity> spec = Specification.where(
                (root, query, cb) -> cb.equal(root.get("tenantId"), params.tenantId())
        );

        if (params.actorId() != null && !params.actorId().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("actorId"), params.actorId()));
        }

        if (params.subjectRef() != null && !params.subjectRef().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("subjectRef"), params.subjectRef()));
        }

        if (params.eventType() != null && !params.eventType().isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("eventType"), params.eventType()));
        }

        if (params.fromTime() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), params.fromTime()));
        }

        if (params.toTime() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), params.toTime()));
        }

        return spec;
    }

    /**
     * Map entity to full response DTO.
     */
    private AuditEventResponse toResponse(AuditEventEntity entity) {
        return new AuditEventResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEventType(),
                entity.getActorId(),
                entity.getActorType(),
                entity.getSubjectRef(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getAction(),
                entity.getOutcome(),
                entity.getPurposeOfUse(),
                entity.getFacilityId(),
                entity.getCorrelationId(),
                entity.getPolicyVersion(),
                entity.getDetail(),
                entity.getPreviousHash(),
                entity.getEntryHash(),
                entity.getSequenceNumber(),
                entity.getCreatedAt()
        );
    }

    /**
     * Map entity to redacted access history entry.
     * Strips internal IDs, resource references, detail payloads, and hashes.
     */
    private AccessHistoryEntry toAccessHistoryEntry(AuditEventEntity entity) {
        return new AccessHistoryEntry(
                entity.getEventType(),
                entity.getActorType(),
                entity.getAction(),
                entity.getOutcome(),
                entity.getPurposeOfUse(),
                entity.getCreatedAt()
        );
    }
}
