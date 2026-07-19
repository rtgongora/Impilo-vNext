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
    private final zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification;

    public AssuranceService(AssuranceRecordRepository recordRepository,
                            AssuranceUpgradeRequestRepository upgradeRepository,
                            EventOutboxRepository outboxRepository,
                            zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification) {
        this.recordRepository = recordRepository;
        this.upgradeRepository = upgradeRepository;
        this.outboxRepository = outboxRepository;
        this.biometricVerification = biometricVerification;
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

    /**
     * Raise the actor's assurance level from an authoritative <b>proofing outcome</b>
     * (Identity Journey Doctrine §4). Unlike {@link #requestUpgrade}/{@link #decideUpgrade},
     * this is not a separate dual-control workflow: the proofing itself IS the governed
     * verification — an auto document-verify with matched liveness, or an officer-witnessed
     * review decision — so its recorded outcome maps directly onto the assurance vector that
     * the PDP reads. Only ever raises (never downgrades) and audits the provenance via the
     * outbox. This is the bridge that makes proofing actually change access decisions.
     */
    @Transactional
    public StatusView recordProofingOutcome(UUID tenantId, String actorId,
                                            AssuranceLevel target, String provenance) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        AssuranceLevel current = recordRepository.findByTenantIdAndActorId(tenantId, actorId)
                .map(AssuranceRecordEntity::getCurrentLevel).orElse(AssuranceLevel.LOA1);
        if (target != null && (current == null || target.isHigherThan(current))) {
            AssuranceRecordEntity record = raiseLevel(tenantId, actorId, target);
            publish("ASSURANCE_RECORD", record.getId(), "ASSURANCE_RAISED_BY_PROOFING", tenantId,
                    Map.of("actorId", actorId, "level", target.name(),
                            "from", current == null ? "NONE" : current.name(),
                            "provenance", provenance == null ? "PROOFING" : provenance));
        }
        return getStatus(tenantId, actorId);
    }

    /**
     * Consume a biometric verification outcome as an <b>LOA4 biometric-binding</b> signal.
     * A live probe is verified against the subject's enrolled template through the shared ABIS
     * seam ({@code zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient}), and the
     * outcome maps onto the assurance vector the PDP reads:
     *
     * <ul>
     *   <li><b>MATCH</b> → the actor is bound by a biometric factor; assurance is asserted at
     *       {@link AssuranceLevel#LOA4} (only ever raising) and the provenance is audited.</li>
     *   <li><b>NO_MATCH</b> → explicit denial: a {@link SecurityException} is thrown and the
     *       existing level is left untouched (a failed biometric never elevates).</li>
     *   <li><b>UNAVAILABLE / NO_REFERENCE</b> → the ABIS is disabled/unreachable or the subject
     *       has no enrolled template; there is no elevation and the current level is unchanged.
     *       Fail-closed on elevation, never a fabricated bind.</li>
     * </ul>
     *
     * @return the actor's assurance status after the (possible) binding
     */
    @Transactional
    public StatusView recordBiometricBinding(UUID tenantId, String actorId, String subjectRef,
                                             String modality, String probeBase64) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (subjectRef == null || subjectRef.isBlank() || probeBase64 == null || probeBase64.isBlank()) {
            throw new IllegalArgumentException("subjectRef and probe are required for a biometric binding");
        }
        String resolvedModality = (modality != null && !modality.isBlank()) ? modality : "FINGERPRINT";
        var decision = biometricVerification.verify(subjectRef, resolvedModality, probeBase64);

        if (decision.isMatch()) {
            AssuranceLevel current = recordRepository.findByTenantIdAndActorId(tenantId, actorId)
                    .map(AssuranceRecordEntity::getCurrentLevel).orElse(AssuranceLevel.LOA1);
            if (current == null || AssuranceLevel.LOA4.isHigherThan(current)) {
                AssuranceRecordEntity record = raiseLevel(tenantId, actorId, AssuranceLevel.LOA4);
                publish("ASSURANCE_RECORD", record.getId(), "ASSURANCE_RAISED_BY_BIOMETRIC", tenantId,
                        Map.of("actorId", actorId, "level", AssuranceLevel.LOA4.name(),
                                "from", current == null ? "NONE" : current.name(),
                                "modality", resolvedModality,
                                "provenance", "BIOMETRIC_MATCH",
                                "confidence", String.valueOf(decision.confidence())));
            }
            return getStatus(tenantId, actorId);
        }

        if ("UNAVAILABLE".equals(decision.result()) || "NO_REFERENCE".equals(decision.result())) {
            // Fail-closed on elevation: no bind on a biometric outage / missing reference.
            return getStatus(tenantId, actorId);
        }

        // NO_MATCH → explicit denial; nothing is raised.
        throw new SecurityException("Biometric NO_MATCH: assurance not elevated for actor " + actorId);
    }

    private AssuranceRecordEntity raiseLevel(UUID tenantId, String actorId, AssuranceLevel level) {
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
        return recordRepository.save(record);
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
