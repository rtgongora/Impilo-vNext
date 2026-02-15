package zw.gov.mohcc.impilo.dags.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dags.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.PolicyEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.PolicyRepository;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final EventOutboxRepository outboxRepository;
    private final AuditService auditService;

    public PolicyService(PolicyRepository policyRepository,
                         EventOutboxRepository outboxRepository,
                         AuditService auditService) {
        this.policyRepository = policyRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PolicyEntity createPolicy(UUID tenantId, String actorId, UUID correlationId,
                                     String name, String description,
                                     String resourceType, String conditions,
                                     PolicyEffect effect) {
        PolicyEntity policy = new PolicyEntity();
        policy.setTenantId(tenantId);
        policy.setName(name);
        policy.setDescription(description);
        policy.setResourceType(resourceType);
        policy.setConditions(conditions != null ? conditions : "{}");
        policy.setEffect(effect != null ? effect : PolicyEffect.DENY);
        policy.setCreatedBy(actorId);
        policy = policyRepository.save(policy);

        publishEvent("POLICY", policy.getId().toString(), "POLICY_CREATED",
                buildPolicyPayload(policy), tenantId.toString(), correlationId);

        auditService.log(tenantId, "POLICY_CREATED", actorId,
                "POLICY", policy.getId().toString(),
                "{\"name\":\"" + name + "\",\"resourceType\":\"" + resourceType + "\"}",
                correlationId);

        return policy;
    }

    @Transactional(readOnly = true)
    public List<PolicyEntity> listPolicies(UUID tenantId) {
        return policyRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<PolicyEntity> findByResourceType(UUID tenantId, String resourceType) {
        return policyRepository.findByTenantIdAndResourceType(tenantId, resourceType);
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

    private String buildPolicyPayload(PolicyEntity policy) {
        return "{\"id\":" + policy.getId()
                + ",\"tenantId\":\"" + policy.getTenantId() + "\""
                + ",\"name\":\"" + policy.getName() + "\""
                + ",\"resourceType\":\"" + policy.getResourceType() + "\""
                + ",\"effect\":\"" + policy.getEffect() + "\""
                + ",\"status\":\"" + policy.getStatus() + "\""
                + "}";
    }
}
