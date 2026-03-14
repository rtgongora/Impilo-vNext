package zw.gov.mohcc.impilo.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchRequest;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchResponse;
import zw.gov.mohcc.impilo.integration.connectors.Connector;
import zw.gov.mohcc.impilo.integration.connectors.ConnectorRegistry;
import zw.gov.mohcc.impilo.integration.connectors.ConnectorRequest;
import zw.gov.mohcc.impilo.integration.connectors.ConnectorResult;
import zw.gov.mohcc.impilo.integration.connectors.ConnectorType;
import zw.gov.mohcc.impilo.integration.domain.DeadLetterEntity;
import zw.gov.mohcc.impilo.integration.domain.DispatchAttemptEntity;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.domain.RouteDefinitionEntity;
import zw.gov.mohcc.impilo.integration.repository.DeadLetterRepository;
import zw.gov.mohcc.impilo.integration.repository.DispatchAttemptRepository;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final RouteService routeService;
    private final TransformService transformService;
    private final MappingTemplateService mappingTemplateService;
    private final ConnectorRegistry connectorRegistry;
    private final DispatchAttemptRepository attemptRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DispatchService(RouteService routeService,
                           TransformService transformService,
                           MappingTemplateService mappingTemplateService,
                           ConnectorRegistry connectorRegistry,
                           DispatchAttemptRepository attemptRepository,
                           DeadLetterRepository deadLetterRepository,
                           OutboxEventRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.routeService = routeService;
        this.transformService = transformService;
        this.mappingTemplateService = mappingTemplateService;
        this.connectorRegistry = connectorRegistry;
        this.attemptRepository = attemptRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DispatchResponse dispatch(DispatchRequest request, RequestContext ctx) {
        String dispatchId = UUID.randomUUID().toString();

        log.info("Dispatching: id={}, method={}, path={}", dispatchId, request.method(), request.path());

        // Find matching route
        Optional<RouteDefinitionEntity> matchedRoute = findMatchingRoute(
                ctx.tenantId(), request.method(), request.path());

        if (matchedRoute.isPresent()) {
            return handleMatchedDispatch(dispatchId, request, matchedRoute.get(), ctx);
        } else {
            return handleUnmatchedDispatch(dispatchId, request, ctx);
        }
    }

    private DispatchResponse handleMatchedDispatch(String dispatchId, DispatchRequest request,
                                                    RouteDefinitionEntity route, RequestContext ctx) {
        // Apply transforms (header and field renames)
        Map<String, String> transformedHeaders = request.headers();
        Map<String, String> headerRules = routeService.deserializeMap(route.getTransformHeadersJson());
        if (headerRules != null) {
            transformedHeaders = transformService.applyHeaderTransform(request.headers(), headerRules);
        }

        Map<String, String> fieldRenameRules = routeService.deserializeMap(route.getTransformFieldRenamesJson());
        String transformedBody = transformService.applyFieldRenames(request.body(), fieldRenameRules);

        // Apply mapping template if route has one
        if (route.getMappingTemplateId() != null) {
            transformedBody = mappingTemplateService.applyMapping(route.getMappingTemplateId(), transformedBody);
        }

        // Resolve connector type
        ConnectorType connectorType = resolveConnectorType(route.getConnectorType());
        Connector connector = connectorRegistry.resolve(connectorType);

        // Build connector request
        Map<String, String> dispatchHeaders = transformedHeaders != null
                ? new HashMap<>(transformedHeaders) : new HashMap<>();
        dispatchHeaders.put("X-Dispatch-ID", dispatchId);
        dispatchHeaders.put("X-Correlation-ID", ctx.correlationId());
        dispatchHeaders.put("X-Tenant-ID", ctx.tenantId());
        dispatchHeaders.put("X-Pod-ID", ctx.podId());

        ConnectorRequest connectorRequest = new ConnectorRequest(
                route.getId(),
                request.method(),
                request.path(),
                dispatchHeaders,
                transformedBody,
                route.getTargetUrl(),
                route.getTargetTimeoutMs()
        );

        // Execute connector
        ConnectorResult connectorResult = connector.execute(connectorRequest);

        String status = connectorResult.success() ? "DISPATCHED" : "DISPATCH_FAILED";

        // Record dispatch attempt
        DispatchAttemptEntity attempt = new DispatchAttemptEntity();
        attempt.setId(dispatchId);
        attempt.setRouteId(route.getId());
        attempt.setMethod(request.method());
        attempt.setPath(request.path());
        attempt.setRequestBody(request.body());
        attempt.setMatched(true);
        attempt.setStatus(status);
        attempt.setTransformedBody(transformedBody);
        if (!connectorResult.success()) {
            attempt.setErrorMessage(connectorResult.errorMessage());
        }
        attempt.setTenantId(ctx.tenantId());
        attempt.setPodId(ctx.podId());
        attempt.setCorrelationId(ctx.correlationId());
        attemptRepository.save(attempt);

        // If dispatch failed, create dead-letter entry
        if (!connectorResult.success()) {
            DeadLetterEntity deadLetter = new DeadLetterEntity();
            deadLetter.setDispatchAttemptId(dispatchId);
            deadLetter.setRouteId(route.getId());
            deadLetter.setMethod(request.method());
            deadLetter.setPath(request.path());
            deadLetter.setRequestBody(request.body());
            deadLetter.setErrorReason(connectorResult.errorMessage() != null
                    ? connectorResult.errorMessage()
                    : "Connector returned failure with status " + connectorResult.statusCode());
            deadLetter.setRetryCount(0);
            deadLetter.setResolved(false);
            deadLetter.setTenantId(ctx.tenantId());
            deadLetter.setPodId(ctx.podId());
            deadLetterRepository.save(deadLetter);
        }

        // Write outbox event
        String eventType = connectorResult.success()
                ? "impilo.integration.dispatch.completed.v1"
                : "impilo.integration.dispatch.failed.v1";
        String idempotencyKey = "dispatch-" + dispatchId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("dispatchId", dispatchId);
        payload.put("routeId", route.getId());
        payload.put("method", request.method());
        payload.put("path", request.path());
        payload.put("targetUrl", route.getTargetUrl());
        payload.put("connectorType", connectorType.name());
        payload.put("matched", true);
        payload.put("success", connectorResult.success());
        payload.put("statusCode", connectorResult.statusCode());
        if (connectorResult.errorMessage() != null) {
            payload.put("errorMessage", connectorResult.errorMessage());
        }

        persistOutboxEvent(eventType, "DispatchAttempt", dispatchId, payload, idempotencyKey, ctx);

        return new DispatchResponse(dispatchId, status, route.getId(), route.getTargetUrl());
    }

    private DispatchResponse handleUnmatchedDispatch(String dispatchId, DispatchRequest request,
                                                      RequestContext ctx) {
        String errorReason = "No matching route found for " + request.method() + " " + request.path();

        // Record dispatch attempt as FAILED
        DispatchAttemptEntity attempt = new DispatchAttemptEntity();
        attempt.setId(dispatchId);
        attempt.setMethod(request.method());
        attempt.setPath(request.path());
        attempt.setRequestBody(request.body());
        attempt.setMatched(false);
        attempt.setStatus("FAILED");
        attempt.setErrorMessage(errorReason);
        attempt.setTenantId(ctx.tenantId());
        attempt.setPodId(ctx.podId());
        attempt.setCorrelationId(ctx.correlationId());
        attemptRepository.save(attempt);

        // Create dead-letter entry
        DeadLetterEntity deadLetter = new DeadLetterEntity();
        deadLetter.setDispatchAttemptId(dispatchId);
        deadLetter.setMethod(request.method());
        deadLetter.setPath(request.path());
        deadLetter.setRequestBody(request.body());
        deadLetter.setErrorReason(errorReason);
        deadLetter.setRetryCount(0);
        deadLetter.setResolved(false);
        deadLetter.setTenantId(ctx.tenantId());
        deadLetter.setPodId(ctx.podId());
        deadLetterRepository.save(deadLetter);

        // Write outbox event for failure
        String idempotencyKey = "dispatch-fail-" + dispatchId;
        persistOutboxEvent(
                "impilo.integration.dispatch.failed.v1",
                "DispatchAttempt",
                dispatchId,
                Map.of(
                        "dispatchId", dispatchId,
                        "method", request.method(),
                        "path", request.path(),
                        "matched", false,
                        "errorReason", errorReason
                ),
                idempotencyKey,
                ctx
        );

        return new DispatchResponse(dispatchId, "FAILED", null, null);
    }

    private ConnectorType resolveConnectorType(String connectorTypeStr) {
        if (connectorTypeStr == null || connectorTypeStr.isBlank()) {
            return ConnectorType.HTTP;
        }
        try {
            return ConnectorType.valueOf(connectorTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown connector type '{}', falling back to HTTP", connectorTypeStr);
            return ConnectorType.HTTP;
        }
    }

    Optional<RouteDefinitionEntity> findMatchingRoute(String tenantId, String method, String path) {
        List<RouteDefinitionEntity> enabledRoutes = routeService.findEnabledRoutes(tenantId);

        for (RouteDefinitionEntity route : enabledRoutes) {
            if (matchesRoute(route, method, path)) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }

    private boolean matchesRoute(RouteDefinitionEntity route, String method, String path) {
        // Check method match
        String routeMethod = route.getMatchMethod();
        if (routeMethod != null && !"*".equals(routeMethod)
                && !routeMethod.equalsIgnoreCase(method)) {
            return false;
        }

        // Check path regex match
        String pathRegex = route.getMatchPathRegex();
        if (pathRegex != null) {
            try {
                if (!Pattern.matches(pathRegex, path)) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                log.warn("Invalid path regex on route {}: {}", route.getId(), pathRegex);
                return false;
            }
        }

        // If no match criteria defined, route does not match dispatch-style requests
        if (routeMethod == null && pathRegex == null) {
            return false;
        }

        return true;
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
