package zw.gov.mohcc.impilo.ia.core;

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

    /**
     * Evaluate risk from the reported signals. The score / level / recommendation are computed
     * SERVER-SIDE by {@link RiskEngine} — never taken from the caller — so a caller cannot
     * declare itself low-risk / ALLOW. The caller supplies only the observed factors and context.
     */
    @Transactional
    public RiskAssessmentEntity assess(UUID tenantId, String actorId, UUID correlationId,
                                       String contextType, String contextId, List<String> factors) {
        RiskEngine.Result result = RiskEngine.evaluate(factors);

        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setTenantId(tenantId);
        entity.setActorId(actorId);
        entity.setContextType(contextType);
        entity.setContextId(contextId);
        entity.setRiskScore(result.score());
        entity.setRiskLevel(result.level());
        entity.setFactors(toJsonArray(factors));
        entity.setRecommendation(result.recommendation());
        entity.setAssessedBy(actorId);
        entity = riskAssessmentRepository.save(entity);

        publishEvent("RISK_ASSESSMENT", entity.getId().toString(), "RISK_ASSESSED",
                buildRiskPayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    private static String toJsonArray(List<String> factors) {
        if (factors == null || factors.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String f : factors) {
            if (f == null || f.isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(f.trim().toUpperCase().replace("\"", "")).append('"');
            first = false;
        }
        return sb.append(']').toString();
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
