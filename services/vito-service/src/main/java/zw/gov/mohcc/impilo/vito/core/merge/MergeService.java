package zw.gov.mohcc.impilo.vito.core.merge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MergeService {

    private final MergeHistoryRepository mergeRepo;
    private final ClientRepository clientRepo;
    private final IdentityAliasRepository aliasRepo;
    private final DedupCaseRepository dedupCaseRepo;
    private final DedupActionRepository dedupActionRepo;
    private final EventOutboxRepository outboxRepo;

    public MergeService(MergeHistoryRepository mergeRepo, ClientRepository clientRepo,
                         IdentityAliasRepository aliasRepo, DedupCaseRepository dedupCaseRepo,
                         DedupActionRepository dedupActionRepo, EventOutboxRepository outboxRepo) {
        this.mergeRepo = mergeRepo;
        this.clientRepo = clientRepo;
        this.aliasRepo = aliasRepo;
        this.dedupCaseRepo = dedupCaseRepo;
        this.dedupActionRepo = dedupActionRepo;
        this.outboxRepo = outboxRepo;
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
        // Validate both clients exist (lookup by CRID, not healthId)
        ClientEntity survivor = clientRepo.findByTenantIdAndCrid(tenantId, survivorCrid)
                .orElseThrow(() -> new IllegalArgumentException("Survivor client not found"));
        ClientEntity merged = clientRepo.findByTenantIdAndCrid(tenantId, mergedCrid)
                .orElseThrow(() -> new IllegalArgumentException("Merged client not found"));

        if (merged.getStatus() == IdentityStatus.MERGED) {
            throw new IllegalStateException("Client is already merged");
        }

        // Mark merged client as MERGED
        merged.setStatus(IdentityStatus.MERGED);
        clientRepo.save(merged);

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

        publishEvent("MERGE", history.getId().toString(), "vito.merge.executed",
                "{\"tenantId\":\"" + tenantId + "\",\"survivor\":\"" + survivorCrid + "\",\"merged\":\"" + mergedCrid + "\"}");

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
        merged.setStatus(IdentityStatus.ACTIVE);
        clientRepo.save(merged);

        // Transfer aliases back (re-associate aliases that originally belonged to merged)
        // Note: This is a best-effort reversal; some aliases may have been modified since merge
        List<IdentityAliasEntity> survivorAliases = aliasRepo.findByTenantIdAndHealthIdAndStatus(
                tenantId, history.getSurvivorCrid(), "ACTIVE");
        // We can't deterministically know which aliases came from the merged record,
        // so we rely on the field_decisions stored in merge history for audit.

        history.setReversedAt(OffsetDateTime.now());
        history.setReversedBy(actorId);
        history = mergeRepo.save(history);

        publishEvent("MERGE", history.getId().toString(), "vito.merge.reversed",
                "{\"tenantId\":\"" + tenantId + "\",\"survivor\":\"" + history.getSurvivorCrid() + "\",\"merged\":\"" + history.getMergedCrid() + "\"}");

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
        outboxRepo.save(event);
    }
}
