package zw.gov.mohcc.impilo.integration.service;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

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
    public RouteResponse upsertRoute(RouteRequest request, RequestContext ctx) {
        RouteDefinitionEntity entity = new RouteDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSourceService(request.sourceService());
        entity.setEventTypePrefix(request.eventTypePrefix());
        entity.setTargetService(request.targetService());
        entity.setTargetUrl(request.targetUrl());
        entity.setEnabled(request.enabled() != null ? request.enabled() : true);
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        entity = routeRepository.save(entity);

        log.info("Upserted route: id={}, source={}, target={}", entity.getId(),
                entity.getSourceService(), entity.getTargetService());

        // Persist outbox event
        String idempotencyKey = "route-upsert-" + entity.getId();
        persistOutboxEvent(
                "impilo.integration.route.upserted.v1",
                "RouteDefinition",
                entity.getId(),
                Map.of(
                        "routeId", entity.getId(),
                        "sourceService", entity.getSourceService(),
                        "eventTypePrefix", entity.getEventTypePrefix(),
                        "targetService", entity.getTargetService(),
                        "targetUrl", entity.getTargetUrl(),
                        "enabled", entity.isEnabled()
                ),
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

    private RouteResponse toResponse(RouteDefinitionEntity entity) {
        return new RouteResponse(
                entity.getId(),
                entity.getSourceService(),
                entity.getEventTypePrefix(),
                entity.getTargetService(),
                entity.getTargetUrl(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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
