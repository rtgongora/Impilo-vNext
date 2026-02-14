package zw.gov.mohcc.impilo.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.rules.api.dto.RuleRequest;
import zw.gov.mohcc.impilo.rules.api.dto.RuleResponse;
import zw.gov.mohcc.impilo.rules.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.rules.domain.RuleEntity;
import zw.gov.mohcc.impilo.rules.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RuleService {

    private final RuleRepository ruleRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public RuleService(RuleRepository ruleRepository,
                       OutboxEventRepository outboxEventRepository,
                       ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RuleResponse upsertRule(RuleRequest request, RequestContext ctx) {
        RuleEntity entity = new RuleEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.name());
        entity.setExpression(request.expression());
        entity.setEnabled(request.isEnabled());
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        RuleEntity saved = ruleRepository.save(entity);

        // Publish outbox event
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setTenantId(ctx.tenantId());
        outboxEvent.setPodId(ctx.podId());
        outboxEvent.setCorrelationId(ctx.correlationId());
        outboxEvent.setEventType("rules.rule.created");
        outboxEvent.setSchemaVersion(1);
        outboxEvent.setOccurredAt(OffsetDateTime.now());
        outboxEvent.setPayloadJson(toJson(Map.of(
                "ruleId", saved.getId(),
                "name", saved.getName(),
                "expression", saved.getExpression(),
                "enabled", saved.isEnabled()
        )));
        outboxEventRepository.save(outboxEvent);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> listRules(String tenantId) {
        return ruleRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RuleResponse toResponse(RuleEntity entity) {
        return new RuleResponse(
                entity.getId(),
                entity.getName(),
                entity.getExpression(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
