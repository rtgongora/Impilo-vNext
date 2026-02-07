package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflineActionRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflineActionResponse;
import zw.gov.mohcc.impilo.tshepo.offline.exception.CapabilityTokenNotFoundException;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineServiceException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.*;

import java.time.Instant;
import java.util.*;

/**
 * Service for logging and querying offline actions.
 *
 * <p>Every action performed while a device is offline is logged here.
 * The rules engine validates each action before it is recorded.
 * Actions that require O-CPID issuance are handled automatically.</p>
 */
@Service
public class OfflineActionService {

    private static final Logger log = LoggerFactory.getLogger(OfflineActionService.class);

    private final OfflineActionLogRepository actionLogRepo;
    private final CapabilityTokenRepository tokenRepo;
    private final EventOutboxRepository outboxRepo;
    private final OfflineRulesEngine rulesEngine;
    private final OCpidIssuanceService oCpidService;
    private final ObjectMapper objectMapper;

    public OfflineActionService(OfflineActionLogRepository actionLogRepo,
                                 CapabilityTokenRepository tokenRepo,
                                 EventOutboxRepository outboxRepo,
                                 OfflineRulesEngine rulesEngine,
                                 OCpidIssuanceService oCpidService,
                                 ObjectMapper objectMapper) {
        this.actionLogRepo = actionLogRepo;
        this.tokenRepo = tokenRepo;
        this.outboxRepo = outboxRepo;
        this.rulesEngine = rulesEngine;
        this.oCpidService = oCpidService;
        this.objectMapper = objectMapper;
    }

    /**
     * Log an offline action after validating it against the rules engine.
     */
    @Transactional
    public OfflineActionResponse logAction(OfflineActionRequest request) {
        log.info("Logging offline action: tenant={}, actor={}, action={}, tokenId={}",
                request.tenantId(), request.actorId(), request.actionType(), request.capabilityTokenId());

        // Step 1: Validate the capability token exists and is active
        CapabilityTokenEntity token = tokenRepo.findByIdAndTenantId(
                        request.capabilityTokenId(), request.tenantId())
                .orElseThrow(() -> new CapabilityTokenNotFoundException(request.capabilityTokenId()));

        if (!"ACTIVE".equals(token.getStatus())) {
            throw new OfflineServiceException("TOKEN_NOT_ACTIVE",
                    "Capability token is not active (status=" + token.getStatus() + ")");
        }

        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw new OfflineServiceException("TOKEN_EXPIRED",
                    "Capability token has expired");
        }

        // Step 2: Parse token capabilities and evaluate via rules engine
        List<String> capabilities = parseCapabilities(token.getCapabilities());
        rulesEngine.evaluateAction(request.actionType(), capabilities, token.getId());

        // Step 3: Issue O-CPID if required
        UUID oCpid = request.oCpid();
        if (rulesEngine.requiresOCpid(request.actionType()) && oCpid == null) {
            oCpid = oCpidService.issueOCpid(request.tenantId(), token.getFacilityId(), request.actorId());
        }

        // Step 4: Persist the action log
        OfflineActionLogEntity entity = new OfflineActionLogEntity();
        entity.setTenantId(request.tenantId());
        entity.setCapabilityTokenId(request.capabilityTokenId());
        entity.setActorId(request.actorId());
        entity.setActionType(request.actionType());
        entity.setResourceType(request.resourceType());
        entity.setResourceRef(request.resourceRef());
        entity.setOCpid(oCpid);
        entity.setPayload(request.payload() != null ? toJson(request.payload()) : null);
        entity.setPerformedAt(request.performedAt());
        entity.setSyncStatus("PENDING");

        entity = actionLogRepo.save(entity);

        // Step 5: Write outbox event
        writeOutboxEvent("OfflineAction", entity.getId().toString(),
                "OFFLINE_ACTION_LOGGED", buildActionEventPayload(entity));

        log.info("Logged offline action id={}, type={}, oCpid={}", entity.getId(), request.actionType(), oCpid);

        return toResponse(entity);
    }

    /**
     * Query offline actions by tenant and actor (paginated).
     */
    @Transactional(readOnly = true)
    public Page<OfflineActionResponse> queryActions(UUID tenantId, String actorId, Pageable pageable) {
        return actionLogRepo.findByTenantAndActor(tenantId, actorId, pageable)
                .map(this::toResponse);
    }

    /**
     * Get all pending (unsynced) actions for a tenant.
     */
    @Transactional(readOnly = true)
    public List<OfflineActionResponse> getPendingActions(UUID tenantId) {
        return actionLogRepo.findPendingByTenant(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private OfflineActionResponse toResponse(OfflineActionLogEntity entity) {
        return new OfflineActionResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getCapabilityTokenId(),
                entity.getActorId(),
                entity.getActionType(),
                entity.getResourceType(),
                entity.getResourceRef(),
                entity.getOCpid(),
                entity.getPerformedAt(),
                entity.getSyncStatus()
        );
    }

    private List<String> parseCapabilities(String capabilitiesJson) {
        try {
            return objectMapper.readValue(capabilitiesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse capabilities JSON: {}", capabilitiesJson, e);
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OfflineServiceException("SERIALIZATION_ERROR", "Failed to serialize to JSON", e);
        }
    }

    private void writeOutboxEvent(String aggregateType, String aggregateId,
                                   String eventType, String payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setCreatedAt(Instant.now());
        outboxRepo.save(outbox);
    }

    private String buildActionEventPayload(OfflineActionLogEntity entity) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("actionId", entity.getId());
        event.put("tenantId", entity.getTenantId().toString());
        event.put("actorId", entity.getActorId());
        event.put("actionType", entity.getActionType());
        event.put("resourceType", entity.getResourceType());
        event.put("resourceRef", entity.getResourceRef());
        if (entity.getOCpid() != null) {
            event.put("oCpid", entity.getOCpid().toString());
        }
        event.put("performedAt", entity.getPerformedAt().toString());
        event.put("timestamp", Instant.now().toString());
        return toJson(event);
    }
}
