package zw.gov.mohcc.impilo.secharden.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.secharden.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.secharden.persistence.entity.PolicyPackEntity;
import zw.gov.mohcc.impilo.secharden.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.secharden.persistence.repository.PolicyPackRepository;

@Service
public class PolicyPackService {

    private final PolicyPackRepository policyPackRepository;
    private final EventOutboxRepository outboxRepository;

    public PolicyPackService(PolicyPackRepository policyPackRepository,
                             EventOutboxRepository outboxRepository) {
        this.policyPackRepository = policyPackRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public PolicyPackEntity createPolicyPack(UUID tenantId, String actorId, UUID correlationId,
                                             String name, String description,
                                             PackType packType, String rules) {
        PolicyPackEntity entity = new PolicyPackEntity();
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setPackType(packType != null ? packType : PackType.BASELINE);
        entity.setRules(rules != null ? rules : "[]");
        entity.setCreatedBy(actorId);
        entity = policyPackRepository.save(entity);

        publishEvent("POLICY_PACK", entity.getId().toString(), "PACK_CREATED",
                buildPackPayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    @Transactional(readOnly = true)
    public List<PolicyPackEntity> listPolicyPacks(UUID tenantId) {
        return policyPackRepository.findByTenantId(tenantId);
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

    private String buildPackPayload(PolicyPackEntity p) {
        return "{\"id\":" + p.getId()
                + ",\"tenantId\":\"" + p.getTenantId() + "\""
                + ",\"name\":\"" + p.getName() + "\""
                + ",\"packType\":\"" + p.getPackType() + "\""
                + ",\"status\":\"" + p.getStatus() + "\""
                + "}";
    }
}
