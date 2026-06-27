package zw.gov.mohcc.impilo.ia.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceRecordEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceUpgradeRequestEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AssuranceRecordRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.AssuranceUpgradeRequestRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Identity assurance level + upgrade-request workflow (Wave C C2a). This is the canonical
 * owner of an actor's assurance level. An upgrade is a reviewed workflow: a request is raised,
 * then a reviewer (who must NOT be the requester — dual control) approves (raising the level)
 * or rejects (with a mandatory reason). Role authorization for who may review is enforced
 * upstream by ext_authz; the service enforces the business invariants and audits every step.
 */
@Service
public class AssuranceService {

    private final AssuranceRecordRepository recordRepository;
    private final AssuranceUpgradeRequestRepository upgradeRepository;
    private final EventOutboxRepository outboxRepository;

    public AssuranceService(AssuranceRecordRepository recordRepository,
                            AssuranceUpgradeRequestRepository upgradeRepository,
                            EventOutboxRepository outboxRepository) {
        this.recordRepository = recordRepository;
        this.upgradeRepository = upgradeRepository;
        this.outboxRepository = outboxRepository;
    }

    /** The assurance status surfaced to the experience shell. */
    public record StatusView(String actorId, AssuranceLevel currentLevel, OffsetDateTime assessedAt,
                             List<String> grantedPermissions, List<String> restrictedPermissions,
                             List<Map<String, Object>> upgradePathways) {}

    @Transactional(readOnly = true)
    public StatusView getStatus(UUID tenantId, String actorId) {
        AssuranceRecordEntity record = recordRepository.findByTenantIdAndActorId(tenantId, actorId).orElse(null);
        AssuranceLevel level = record != null ? record.getCurrentLevel() : AssuranceLevel.LOA1;
        OffsetDateTime assessedAt = record != null ? record.getAssessedAt() : null;
        return new StatusView(actorId, level, assessedAt,
                AssurancePolicy.granted(level), AssurancePolicy.restricted(level), AssurancePolicy.pathways(level));
    }

    @Transactional
    public AssuranceUpgradeRequestEntity requestUpgrade(UUID tenantId, String actorId,
                                                        AssuranceLevel targetLevel, String method) {
        AssuranceLevel current = recordRepository.findByTenantIdAndActorId(tenantId, actorId)
                .map(AssuranceRecordEntity::getCurrentLevel).orElse(AssuranceLevel.LOA1);
        if (!targetLevel.isHigherThan(current)) {
            throw new IllegalArgumentException(
                    "Target level " + targetLevel + " must be higher than current level " + current);
        }
        AssuranceUpgradeRequestEntity request = new AssuranceUpgradeRequestEntity();
        request.setTenantId(tenantId);
        request.setActorId(actorId);
        request.setCurrentLevel(current);
        request.setTargetLevel(targetLevel);
        request.setMethod(method != null ? method : "IN_PERSON_VERIFICATION");
        request.setStatus(UpgradeStatus.PENDING);
        request = upgradeRepository.save(request);

        publish("ASSURANCE_UPGRADE", request.getId(), "ASSURANCE_UPGRADE_REQUESTED",
                tenantId, Map.of("actorId", actorId, "targetLevel", targetLevel.name()));
        return request;
    }

    @Transactional(readOnly = true)
    public List<AssuranceUpgradeRequestEntity> listPending(UUID tenantId) {
        return upgradeRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, UpgradeStatus.PENDING);
    }

    /**
     * Approve or reject a pending upgrade. Dual control: the reviewer must not be the requester.
     * Approval raises the actor's assurance level; rejection requires a reason.
     */
    @Transactional
    public AssuranceUpgradeRequestEntity decideUpgrade(UUID tenantId, String reviewerActorId, Long requestId,
                                                       boolean approve, String reason) {
        AssuranceUpgradeRequestEntity request = upgradeRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Upgrade request not found: " + requestId));
        if (request.getStatus() != UpgradeStatus.PENDING) {
            throw new IllegalStateException("Upgrade request is not pending: " + request.getStatus());
        }
        if (reviewerActorId == null || reviewerActorId.equals(request.getActorId())) {
            throw new SecurityException("Dual control: a reviewer cannot decide their own assurance upgrade");
        }

        request.setDecidedBy(reviewerActorId);
        request.setDecidedAt(OffsetDateTime.now());

        if (approve) {
            request.setStatus(UpgradeStatus.APPROVED);
            upgradeRepository.save(request);
            raiseLevel(tenantId, request.getActorId(), request.getTargetLevel());
            publish("ASSURANCE_UPGRADE", requestId, "ASSURANCE_UPGRADE_APPROVED",
                    tenantId, Map.of("actorId", request.getActorId(),
                            "targetLevel", request.getTargetLevel().name(), "reviewer", reviewerActorId));
        } else {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("A rejection reason is required");
            }
            request.setStatus(UpgradeStatus.REJECTED);
            request.setReason(reason);
            upgradeRepository.save(request);
            publish("ASSURANCE_UPGRADE", requestId, "ASSURANCE_UPGRADE_REJECTED",
                    tenantId, Map.of("actorId", request.getActorId(), "reviewer", reviewerActorId));
        }
        return request;
    }

    private void raiseLevel(UUID tenantId, String actorId, AssuranceLevel level) {
        AssuranceRecordEntity record = recordRepository.findByTenantIdAndActorId(tenantId, actorId)
                .orElseGet(() -> {
                    AssuranceRecordEntity e = new AssuranceRecordEntity();
                    e.setTenantId(tenantId);
                    e.setActorId(actorId);
                    return e;
                });
        // Only ever raise — never silently downgrade via an upgrade workflow.
        if (record.getCurrentLevel() == null || level.isHigherThan(record.getCurrentLevel())) {
            record.setCurrentLevel(level);
        }
        record.setAssessedAt(OffsetDateTime.now());
        recordRepository.save(record);
    }

    private void publish(String aggregateType, Long aggregateId, String eventType,
                         UUID tenantId, Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append('"').append(e.getKey()).append("\":\"")
                    .append(String.valueOf(e.getValue()).replace("\"", "'")).append('"');
            first = false;
        }
        json.append('}');

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(String.valueOf(aggregateId));
        event.setEventType(eventType);
        event.setPayload(json.toString());
        event.setTenantId(tenantId.toString());
        outboxRepository.save(event);
    }
}
