package zw.gov.mohcc.impilo.integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.integration.api.dto.RouteRequest;
import zw.gov.mohcc.impilo.integration.api.dto.RouteResponse;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.domain.RouteDefinitionEntity;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.integration.repository.RouteDefinitionRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final RouteDefinitionRepository routeRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RouteService(RouteDefinitionRepository routeRepository,
                        OutboxEventRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RouteResponse createRoute(RouteRequest request, RequestContext ctx) {
        RouteDefinitionEntity entity = new RouteDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSourceService(request.sourceService());
        entity.setEventTypePrefix(request.eventTypePrefix());
        entity.setTargetService(request.targetService());
        entity.setTargetUrl(request.targetUrl());
        entity.setEnabled(request.enabled() != null ? request.enabled() : true);
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        // v2 fields
        entity.setMatchMethod(request.matchMethod());
        entity.setMatchPathRegex(request.matchPathRegex());
        entity.setTargetTimeoutMs(request.targetTimeoutMs());
        entity.setTransformHeadersJson(serializeMap(request.transformHeaders()));
        entity.setTransformFieldRenamesJson(serializeMap(request.transformFieldRenames()));

        // v3 fields
        entity.setConnectorType(request.connectorType() != null ? request.connectorType() : "HTTP");
        entity.setMappingTemplateId(request.mappingTemplateId());

        entity = routeRepository.save(entity);

        log.info("Created route: id={}, matchMethod={}, matchPathRegex={}, targetUrl={}",
                entity.getId(), entity.getMatchMethod(), entity.getMatchPathRegex(), entity.getTargetUrl());

        String idempotencyKey = "route-create-" + entity.getId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeId", entity.getId());
        payload.put("targetUrl", entity.getTargetUrl());
        payload.put("enabled", entity.isEnabled());
        if (entity.getMatchMethod() != null) payload.put("matchMethod", entity.getMatchMethod());
        if (entity.getMatchPathRegex() != null) payload.put("matchPathRegex", entity.getMatchPathRegex());
        if (entity.getTargetTimeoutMs() != null) payload.put("targetTimeoutMs", entity.getTargetTimeoutMs());
        if (entity.getSourceService() != null) payload.put("sourceService", entity.getSourceService());
        if (entity.getTargetService() != null) payload.put("targetService", entity.getTargetService());
        if (entity.getConnectorType() != null) payload.put("connectorType", entity.getConnectorType());
        if (entity.getMappingTemplateId() != null) payload.put("mappingTemplateId", entity.getMappingTemplateId());

        persistOutboxEvent(
                "impilo.integration.route.created.v1",
                "RouteDefinition",
                entity.getId(),
                payload,
                idempotencyKey,
                ctx
        );

        return toResponse(entity);
    }

    public List<RouteResponse> listRoutes(String tenantId) {
        return routeRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<RouteDefinitionEntity> findEnabledRoutes(String tenantId) {
        return routeRepository.findByTenantIdAndEnabled(tenantId, true);
    }

    RouteResponse toResponse(RouteDefinitionEntity entity) {
        return new RouteResponse(
                entity.getId(),
                entity.getSourceService(),
                entity.getEventTypePrefix(),
                entity.getTargetService(),
                entity.getTargetUrl(),
                entity.isEnabled(),
                entity.getMatchMethod(),
                entity.getMatchPathRegex(),
                deserializeMap(entity.getTransformHeadersJson()),
                deserializeMap(entity.getTransformFieldRenamesJson()),
                entity.getTargetTimeoutMs(),
                entity.getConnectorType(),
                entity.getMappingTemplateId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String serializeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to serialize map", e);
            return null;
        }
    }

    Map<String, String> deserializeMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.error("Failed to deserialize map from JSON: {}", json, e);
            return null;
        }
    }

    private void persistOutboxEvent(String eventType, String subjectType, String subjectId,
                                    Map<String, Object> payload, String idempotencyKey,
                                    RequestContext ctx) {
        try {
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventType(eventType)
                    .schemaVersion(1)
                    .correlationId(ctx.correlationId())
                    .causationId(ctx.requestId())
                    .idempotencyKey(idempotencyKey)
                    .producer("integration-hub")
                    .tenantId(ctx.tenantId())
                    .podId(ctx.podId())
                    .occurredAt(OffsetDateTime.now())
                    .subjectType(subjectType)
                    .subjectId(subjectId)
                    .payload(payload)
                    .build();

            OutboxEventEntity outbox = new OutboxEventEntity();
            outbox.setTenantId(ctx.tenantId());
            outbox.setPodId(ctx.podId());
            outbox.setCorrelationId(ctx.correlationId());
            outbox.setIdempotencyKey(idempotencyKey);
            outbox.setEventType(eventType);
            outbox.setSchemaVersion(1);
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setPayloadJson(objectMapper.writeValueAsString(envelope));

            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to persist outbox event: {}", eventType, e);
        }
    }
}
