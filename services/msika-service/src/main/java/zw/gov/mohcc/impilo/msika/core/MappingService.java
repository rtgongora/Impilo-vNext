package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalMappingEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ExternalMappingRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;

@Service
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);

    private final ExternalMappingRepository mappingRepository;
    private final EventOutboxRepository outboxRepository;
    private final ChangeLogService changeLogService;
    private final ObjectMapper objectMapper;

    public MappingService(ExternalMappingRepository mappingRepository,
                          EventOutboxRepository outboxRepository,
                          ChangeLogService changeLogService,
                          ObjectMapper objectMapper) {
        this.mappingRepository = mappingRepository;
        this.outboxRepository = outboxRepository;
        this.changeLogService = changeLogService;
        this.objectMapper = objectMapper;
    }

    public Page<ExternalMappingEntity> getPendingMappings(Pageable pageable) {
        return mappingRepository.findByStatus("PENDING", pageable);
    }

    @Transactional
    public ExternalMappingEntity approveMapping(String mappingId) {
        TrustContext ctx = TrustContextHolder.require();
        ExternalMappingEntity mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        if (!"PENDING".equals(mapping.getStatus())) {
            throw new IllegalStateException("Mapping is not in PENDING status: " + mapping.getStatus());
        }

        mapping.setStatus("APPROVED");
        mapping.setReviewedBy(ctx.actorId());
        mapping.setReviewedAt(OffsetDateTime.now());
        mapping = mappingRepository.save(mapping);

        // Emit outbox event
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("MAPPING");
        event.setAggregateId(mappingId);
        event.setEventType("MAPPING_APPROVED");
        event.setTenantId(ctx.tenantId());
        try {
            event.setPayload(objectMapper.writeValueAsString(mapping));
        } catch (Exception e) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);

        changeLogService.record("APPROVE", "MAPPING", mappingId, "Approved by " + ctx.actorId());
        log.info("Approved mapping {} (external={} -> internal={})",
                mappingId, mapping.getExternalCode(), mapping.getInternalItemId());
        return mapping;
    }

    @Transactional
    public ExternalMappingEntity rejectMapping(String mappingId) {
        TrustContext ctx = TrustContextHolder.require();
        ExternalMappingEntity mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        if (!"PENDING".equals(mapping.getStatus())) {
            throw new IllegalStateException("Mapping is not in PENDING status: " + mapping.getStatus());
        }

        mapping.setStatus("REJECTED");
        mapping.setReviewedBy(ctx.actorId());
        mapping.setReviewedAt(OffsetDateTime.now());
        mapping = mappingRepository.save(mapping);

        changeLogService.record("REJECT", "MAPPING", mappingId, "Rejected by " + ctx.actorId());
        log.info("Rejected mapping {} (external={})", mappingId, mapping.getExternalCode());
        return mapping;
    }
}
