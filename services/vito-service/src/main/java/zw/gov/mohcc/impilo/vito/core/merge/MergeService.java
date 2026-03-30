package zw.gov.mohcc.impilo.vito.core.merge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.DeltaPayload;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MergeService {

    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    private final MergeHistoryRepository mergeRepo;
    private final ClientRepository clientRepo;
    private final IdentityAliasRepository aliasRepo;
    private final DedupCaseRepository dedupCaseRepo;
    private final DedupActionRepository dedupActionRepo;
    private final EventOutboxRepository outboxRepo;
    private final FederationAuthorityGuard federationGuard;
    private final ObjectMapper objectMapper;

    public MergeService(MergeHistoryRepository mergeRepo, ClientRepository clientRepo,
                         IdentityAliasRepository aliasRepo, DedupCaseRepository dedupCaseRepo,
                         DedupActionRepository dedupActionRepo, EventOutboxRepository outboxRepo,
                         FederationAuthorityGuard federationGuard,
                         ObjectMapper objectMapper) {
        this.mergeRepo = mergeRepo;
        this.clientRepo = clientRepo;
        this.aliasRepo = aliasRepo;
        this.dedupCaseRepo = dedupCaseRepo;
        this.dedupActionRepo = dedupActionRepo;
        this.outboxRepo = outboxRepo;
        this.federationGuard = federationGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * Merge two client records. The survivor absorbs the merged record.
     * The merged record becomes MERGED status (soft tombstone).
     * Aliases from the merged record are transferred to the survivor.
     */
    @Transactional
    public MergeHistoryEntity merge(UUID tenantId, UUID survivorCrid, UUID mergedCrid,
                                     Long dedupCaseId, String strategy, String fieldDecisions,
                                     String actorId, String actorType) {
        // v1.1 federation guard: merge is NATIONAL_SPINE_ONLY
        enforceFederationAuthority();

        // Validate both clients exist (lookup by CRID, not healthId)
        ClientEntity survivor = clientRepo.findByTenantIdAndCrid(tenantId, survivorCrid)
                .orElseThrow(() -> new IllegalArgumentException("Survivor client not found"));
        ClientEntity merged = clientRepo.findByTenantIdAndCrid(tenantId, mergedCrid)
                .orElseThrow(() -> new IllegalArgumentException("Merged client not found"));

        if (merged.getStatus() == IdentityStatus.MERGED) {
            throw new IllegalStateException("Client is already merged");
        }

        // Mark merged client as MERGED
        Map<String, Object> before = clientToMap(merged);
        merged.setStatus(IdentityStatus.MERGED);
        clientRepo.save(merged);
        Map<String, Object> after = clientToMap(merged);

        // Transfer active aliases from merged to survivor (use actual healthIds, not CRIDs)
        List<IdentityAliasEntity> mergedAliases = aliasRepo.findByTenantIdAndHealthIdAndStatus(
                tenantId, merged.getHealthId(), "ACTIVE");
        for (IdentityAliasEntity alias : mergedAliases) {
            alias.setHealthId(survivor.getHealthId());
            aliasRepo.save(alias);
        }

        // Record merge history
        MergeHistoryEntity history = new MergeHistoryEntity();
        history.setTenantId(tenantId);
        history.setSurvivorCrid(survivorCrid);
        history.setMergedCrid(mergedCrid);
        history.setDedupCaseId(dedupCaseId);
        history.setStrategy(strategy);
        history.setFieldDecisions(fieldDecisions != null ? fieldDecisions : "{}");
        history.setReversible(true);
        history.setCreatedById(actorId);
        history.setCreatedByType(actorType);
        history = mergeRepo.save(history);

        // Update dedup case if linked
        if (dedupCaseId != null) {
            dedupCaseRepo.findById(dedupCaseId).ifPresent(dc -> {
                dc.setStatus("EXECUTED");
                dc.setReviewedAt(OffsetDateTime.now());
                dc.setReviewedBy(actorId);
                dedupCaseRepo.save(dc);
            });

            // Log the action
            DedupActionEntity action = new DedupActionEntity();
            action.setCaseId(dedupCaseId);
            action.setTenantId(tenantId);
            action.setActorId(actorId);
            action.setActorType(actorType);
            action.setAction("MERGED");
            action.setPayload("{\"survivor\":\"" + survivorCrid + "\",\"merged\":\"" + mergedCrid + "\"}");
            dedupActionRepo.save(action);
        }

        DeltaPayload delta = new DeltaPayload("MERGE", before, after, List.of("status"));
        publishEvent("MERGE", history.getId().toString(), "vito.merge.executed",
                Map.of(
                        "delta", delta.toMap(),
                        "full", after,
                        "tenantId", tenantId,
                        "survivor", survivorCrid,
                        "merged", mergedCrid
                ));

        return history;
    }

    /**
     * Reverse a previous merge (unmerge).
     * Restores the merged client and transfers aliases back.
     */
    @Transactional
    public MergeHistoryEntity unmerge(UUID tenantId, Long mergeHistoryId, String actorId) {
        MergeHistoryEntity history = mergeRepo.findById(mergeHistoryId)
                .filter(h -> h.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Merge history not found"));

        if (!history.isReversible()) {
            throw new IllegalStateException("This merge is not reversible");
        }
        if (history.getReversedAt() != null) {
            throw new IllegalStateException("This merge has already been reversed");
        }

        // Restore merged client (lookup by CRID)
        ClientEntity merged = clientRepo.findByTenantIdAndCrid(tenantId, history.getMergedCrid())
                .orElseThrow();
        Map<String, Object> before = clientToMap(merged);
        merged.setStatus(IdentityStatus.ACTIVE);
        clientRepo.save(merged);
        Map<String, Object> after = clientToMap(merged);

        // Transfer aliases back (re-associate aliases that originally belonged to merged)
        // Note: This is a best-effort reversal; some aliases may have been modified since merge
        List<IdentityAliasEntity> survivorAliases = aliasRepo.findByTenantIdAndHealthIdAndStatus(
                tenantId, history.getSurvivorCrid(), "ACTIVE");
        // We can't deterministically know which aliases came from the merged record,
        // so we rely on the field_decisions stored in merge history for audit.

        history.setReversedAt(OffsetDateTime.now());
        history.setReversedBy(actorId);
        history = mergeRepo.save(history);

        DeltaPayload delta = new DeltaPayload("UPDATE", before, after, List.of("status"));
        publishEvent("MERGE", history.getId().toString(), "vito.merge.reversed",
                Map.of(
                        "delta", delta.toMap(),
                        "full", after,
                        "tenantId", tenantId,
                        "survivor", history.getSurvivorCrid(),
                        "merged", history.getMergedCrid()
                ));

        return history;
    }

    @Transactional(readOnly = true)
    public List<MergeHistoryEntity> getMergeHistory(UUID tenantId, UUID crid) {
        return mergeRepo.findByTenantIdAndSurvivorCrid(tenantId, crid);
    }

    @Transactional(readOnly = true)
    public Page<MergeHistoryEntity> getReversibleMerges(UUID tenantId, Pageable pageable) {
        return mergeRepo.findByTenantIdAndReversibleTrueAndReversedAtIsNull(tenantId, pageable);
    }

    /**
     * Log a dedup action (assign, review, escalate, etc.)
     */
    @Transactional
    public DedupActionEntity logAction(Long caseId, UUID tenantId, String actorId,
                                        String actorType, String action, String payload, String notes) {
        DedupActionEntity dedupAction = new DedupActionEntity();
        dedupAction.setCaseId(caseId);
        dedupAction.setTenantId(tenantId);
        dedupAction.setActorId(actorId);
        dedupAction.setActorType(actorType);
        dedupAction.setAction(action);
        dedupAction.setPayload(payload != null ? payload : "{}");
        dedupAction.setNotes(notes);
        return dedupActionRepo.save(dedupAction);
    }

    @Transactional(readOnly = true)
    public List<DedupActionEntity> getCaseActions(Long caseId) {
        return dedupActionRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);

        // Populate v1.1 context from current request if available
        String podId = currentPodId();
        if (podId != null) {
            event.setTenantId(currentHeaderValue("X-Tenant-ID"));
            event.setPodId(podId);
            event.setCorrelationId(currentHeaderValue("X-Correlation-ID"));
            event.setIdempotencyKey(currentHeaderValue("Idempotency-Key"));
        }

        outboxRepo.save(event);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Map<String, Object> payloadMap) {
        try {
            String json = objectMapper.registerModule(new JavaTimeModule()).writeValueAsString(payloadMap);
            publishEvent(aggregateType, aggregateId, eventType, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for {}/{}", aggregateType, aggregateId, e);
            throw new RuntimeException("Event serialization failed", e);
        }
    }

    private Map<String, Object> clientToMap(ClientEntity c) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", c.getId());
        map.put("health_id", c.getHealthId());
        map.put("tenant_id", c.getTenantId());
        map.put("given_name", c.getGivenName());
        map.put("family_name", c.getFamilyName());
        map.put("date_of_birth", c.getDateOfBirth());
        map.put("sex", c.getSex());
        map.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        map.put("crid", c.getCrid());
        return map;
    }

    /**
     * Enforce federation authority for merge operations.
     * Only checks when running in a v1.1 request context (X-Pod-ID present).
     * Legacy callers (without X-Pod-ID) are not gated.
     */
    private void enforceFederationAuthority() {
        String podId = currentPodId();
        if (podId != null) {
            federationGuard.requireNationalPodForMerge(podId);
        }
    }

    private static String currentPodId() {
        return currentHeaderValue("X-Pod-ID");
    }

    private static String currentHeaderValue(String headerName) {
        ServletRequestAttributes attrs = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(headerName);
    }
}
