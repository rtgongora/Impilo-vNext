package zw.gov.mohcc.impilo.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.rules.api.dto.RuleRequest;
import zw.gov.mohcc.impilo.rules.api.dto.RuleResponse;
import zw.gov.mohcc.impilo.rules.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.rules.domain.RuleEntity;
import zw.gov.mohcc.impilo.rules.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
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
    public RuleResponse createRule(RuleRequest request, RequestContext ctx) {
        // Check for duplicate key
        ruleRepository.findByKeyAndTenantId(request.key(), ctx.tenantId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Rule with key '" + request.key() + "' already exists");
                });

        RuleEntity entity = new RuleEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setKey(request.key());
        entity.setName(request.name() != null ? request.name() : request.key());
        entity.setStatus("ACTIVE");
        entity.setEnabled(true);
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        RuleEntity saved = ruleRepository.save(entity);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ruleId", saved.getId());
        payload.put("key", saved.getKey());
        payload.put("name", saved.getName());
        payload.put("status", saved.getStatus());
        persistOutboxEvent("impilo.rules.rule.created.v1", payload, ctx);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> listRules(String tenantId) {
        return ruleRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public RuleEntity findByKeyOrThrow(String key, String tenantId) {
        return ruleRepository.findByKeyAndTenantId(key, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rule not found: " + key));
    }

    public RuleEntity save(RuleEntity entity) {
        return ruleRepository.save(entity);
    }

    RuleResponse toResponse(RuleEntity entity) {
        return new RuleResponse(
                entity.getId(),
                entity.getKey(),
                entity.getName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    void persistOutboxEvent(String eventType, Map<String, Object> payload, RequestContext ctx) {
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setTenantId(ctx.tenantId());
        outboxEvent.setPodId(ctx.podId());
        outboxEvent.setCorrelationId(ctx.correlationId());
        outboxEvent.setEventType(eventType);
        outboxEvent.setSchemaVersion(1);
        outboxEvent.setOccurredAt(OffsetDateTime.now());
        outboxEvent.setPayloadJson(toJson(payload));
        outboxEventRepository.save(outboxEvent);
    }

    String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
