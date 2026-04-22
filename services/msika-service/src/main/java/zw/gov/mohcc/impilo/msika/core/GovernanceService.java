package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.GovernanceDtos;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.GovernanceRecordEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.GovernanceRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;

@Service
public class GovernanceService {

    private final GovernanceRecordRepository recordRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public GovernanceService(GovernanceRecordRepository recordRepository,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GovernanceDtos.GovernanceRecordView create(GovernanceDtos.CreateGovernanceRecordRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        GovernanceRecordEntity e = new GovernanceRecordEntity();
        e.setRecordId(UlidGenerator.generate());
        e.setTenantId(req.tenantId() != null ? req.tenantId() : ctx.tenantId());
        e.setTargetType(req.targetType());
        e.setTargetId(req.targetId());
        e.setReviewType(req.reviewType());
        e.setNotes(req.notes());
        e.setEvidenceRefsJson(JsonSupport.toJsonSafe(objectMapper, req.evidenceRefs(), "[]"));
        e.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        e = recordRepository.save(e);
        emit("GOVERNANCE", e.getRecordId(), "GOVERNANCE_RECORD_CREATED", e, ctx);
        return toView(e);
    }

    @Transactional
    public GovernanceDtos.GovernanceRecordView review(String recordId, GovernanceDtos.ReviewActionRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        GovernanceRecordEntity e = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Governance record not found: " + recordId));

        String status = req.status() != null ? req.status() : e.getStatus();
        e.markReviewed(ctx.actorId(), status, req.decision(), req.notes());
        e = recordRepository.save(e);

        emit("GOVERNANCE", e.getRecordId(), "GOVERNANCE_DECISION_RECORDED", e, ctx);
        return toView(e);
    }

    public List<GovernanceDtos.GovernanceRecordView> listForTarget(String targetType, String targetId) {
        return recordRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId)
                .stream().map(this::toView).toList();
    }

    public List<GovernanceDtos.GovernanceRecordView> queue(String status) {
        String s = status != null ? status : "PENDING";
        return recordRepository.findTop100ByStatusOrderByCreatedAtDesc(s)
                .stream().map(this::toView).toList();
    }

    private GovernanceDtos.GovernanceRecordView toView(GovernanceRecordEntity e) {
        return new GovernanceDtos.GovernanceRecordView(
                e.getRecordId(),
                e.getTenantId(),
                e.getTargetType(),
                e.getTargetId(),
                e.getReviewType(),
                e.getStatus(),
                e.getReviewerRef(),
                e.getDecision(),
                e.getNotes(),
                JsonSupport.parseJsonSafe(objectMapper, e.getEvidenceRefsJson(), List.of()),
                e.getReviewedAt(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private void emit(String aggregateType, String aggregateId, String eventType, Object payload, TrustContext ctx) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTenantId(ctx.tenantId());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);
    }
}

