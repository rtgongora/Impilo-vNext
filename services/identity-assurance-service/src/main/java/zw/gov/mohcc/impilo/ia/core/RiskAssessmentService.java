package zw.gov.mohcc.impilo.ia.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.RiskAssessmentEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.RiskAssessmentRepository;

@Service
public class RiskAssessmentService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final EventOutboxRepository outboxRepository;

    public RiskAssessmentService(RiskAssessmentRepository riskAssessmentRepository,
                                 EventOutboxRepository outboxRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public RiskAssessmentEntity assess(UUID tenantId, String actorId, UUID correlationId,
                                       String contextType, String contextId,
                                       BigDecimal riskScore, RiskLevel riskLevel,
                                       String factors, Recommendation recommendation) {
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setTenantId(tenantId);
        entity.setActorId(actorId);
        entity.setContextType(contextType);
        entity.setContextId(contextId);
        entity.setRiskScore(riskScore != null ? riskScore : BigDecimal.ZERO);
        entity.setRiskLevel(riskLevel != null ? riskLevel : RiskLevel.LOW);
        entity.setFactors(factors != null ? factors : "[]");
        entity.setRecommendation(recommendation != null ? recommendation : Recommendation.ALLOW);
        entity.setAssessedBy(actorId);
        entity = riskAssessmentRepository.save(entity);

        publishEvent("RISK_ASSESSMENT", entity.getId().toString(), "RISK_ASSESSED",
                buildRiskPayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    @Transactional(readOnly = true)
    public List<RiskAssessmentEntity> listAssessments(UUID tenantId) {
        return riskAssessmentRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<RiskAssessmentEntity> findByActor(UUID tenantId, String actorId) {
        return riskAssessmentRepository.findByTenantIdAndActorId(tenantId, actorId);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }

    private String buildRiskPayload(RiskAssessmentEntity r) {
        return "{\"id\":" + r.getId()
                + ",\"tenantId\":\"" + r.getTenantId() + "\""
                + ",\"actorId\":\"" + r.getActorId() + "\""
                + ",\"contextType\":\"" + r.getContextType() + "\""
                + ",\"riskScore\":" + r.getRiskScore()
                + ",\"riskLevel\":\"" + r.getRiskLevel() + "\""
                + ",\"recommendation\":\"" + r.getRecommendation() + "\""
                + "}";
    }
}
