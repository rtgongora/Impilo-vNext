package zw.gov.mohcc.impilo.participation.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.participation.domain.ContributionLifecycle;
import zw.gov.mohcc.impilo.participation.domain.ContributionType;
import zw.gov.mohcc.impilo.participation.domain.ModerationState;
import zw.gov.mohcc.impilo.participation.events.ParticipationEventEmitter;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEventEntity;
import zw.gov.mohcc.impilo.participation.persistence.repository.ContributionEventRepository;
import zw.gov.mohcc.impilo.participation.persistence.repository.ContributionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The owning service for the citizen contribution aggregate. Implements the co-design
 * operating cycle Receive → Community review → Explore → Research → Select for co-design →
 * Plan → Build → Ready for testing → Release.
 *
 * <p>Hard rules enforced here:
 * <ul>
 *   <li>Every status change is validated against {@link ContributionLifecycle}; illegal
 *       jumps are rejected rather than silently applied.</li>
 *   <li>Every meaningful action writes a {@code contribution_event} row and emits an
 *       outbox event.</li>
 *   <li>Public-board visibility is a separate moderation axis — a contribution is only
 *       shown on the public board once {@code moderation_state = APPROVED}.</li>
 * </ul>
 */
@Service
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final ContributionEventRepository eventRepository;
    private final ReferenceGenerator referenceGenerator;
    private final ParticipationEventEmitter emitter;

    public ContributionService(ContributionRepository contributionRepository,
                               ContributionEventRepository eventRepository,
                               ReferenceGenerator referenceGenerator,
                               ParticipationEventEmitter emitter) {
        this.contributionRepository = contributionRepository;
        this.eventRepository = eventRepository;
        this.referenceGenerator = referenceGenerator;
        this.emitter = emitter;
    }

    /** Command carrying the fields needed to open a contribution (public or authenticated). */
    public record NewContribution(
            String type, String needArea, String title, String problemOrOpportunity,
            String betterExperience, String beneficiary, boolean willingToHelp,
            String submitterMode, String submitterRef, Map<String, Object> metadata) {
    }

    @Transactional
    public ContributionEntity create(UUID tenantId, NewContribution cmd) {
        String type = require(cmd.type(), "type");
        if (!ContributionType.isKnown(type)) {
            throw new IllegalArgumentException("Unknown contribution type: " + type);
        }
        if ((cmd.title() == null || cmd.title().isBlank())
                && (cmd.problemOrOpportunity() == null || cmd.problemOrOpportunity().isBlank())) {
            throw new IllegalArgumentException("A title or a description of the problem/opportunity is required.");
        }
        String submitterMode = "AUTHENTICATED".equalsIgnoreCase(cmd.submitterMode())
                ? "AUTHENTICATED" : "ANONYMOUS";

        ContributionEntity c = new ContributionEntity();
        c.setTenantId(tenantId);
        c.setReference(referenceGenerator.generate("IDEA",
                ref -> contributionRepository.existsByTenantIdAndReference(tenantId, ref)));
        c.setType(type);
        c.setNeedArea(cmd.needArea());
        c.setTitle(cmd.title());
        c.setProblemOrOpportunity(cmd.problemOrOpportunity());
        c.setBetterExperience(cmd.betterExperience());
        c.setBeneficiary(cmd.beneficiary());
        c.setWillingToHelp(cmd.willingToHelp());
        c.setSubmitterMode(submitterMode);
        if ("AUTHENTICATED".equals(submitterMode)) {
            c.setSubmitterRef(cmd.submitterRef());
            c.setCreatedBy(cmd.submitterRef());
        }
        c.setStatus(ContributionLifecycle.RECEIVED);
        c.setModerationState(ModerationState.PENDING);
        c.setSupportCount(0);
        c.setMetadataJson(JsonUtil.toJson(cmd.metadata()));
        contributionRepository.save(c);

        recordEvent(c, "RECEIVED", null, c.getStatus(), cmd.submitterRef(), "Contribution received");
        emit(c, "participation.contribution.received");
        return c;
    }

    @Transactional(readOnly = true)
    public ContributionEntity get(UUID tenantId, UUID id) {
        return contributionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Contribution not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ContributionEntity> list(UUID tenantId, String status, String type) {
        if (status != null && !status.isBlank()) {
            return contributionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        }
        if (type != null && !type.isBlank()) {
            return contributionRepository.findByTenantIdAndTypeOrderByCreatedAtDesc(tenantId, type);
        }
        return contributionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<ContributionEventEntity> timeline(UUID tenantId, UUID id) {
        get(tenantId, id);
        return eventRepository.findByContributionIdOrderByOccurredAtAsc(id);
    }

    /** Guarded lifecycle transition. */
    @Transactional
    public ContributionEntity transition(UUID tenantId, UUID id, String toStatus, String note, String actorRef) {
        ContributionEntity c = get(tenantId, id);
        if (!ContributionLifecycle.isKnown(toStatus)) {
            throw new IllegalArgumentException("Unknown status: " + toStatus);
        }
        String from = c.getStatus();
        if (!ContributionLifecycle.canTransition(from, toStatus)) {
            throw new IllegalStateException("Illegal transition " + from + " → " + toStatus);
        }
        c.setStatus(toStatus);
        contributionRepository.save(c);
        recordEvent(c, "STATUS_CHANGED", from, toStatus, actorRef, note);
        emit(c, "participation.contribution.status_changed");
        return c;
    }

    /** Approve/reject/return-to-pending public-board visibility (separate from lifecycle). */
    @Transactional
    public ContributionEntity moderate(UUID tenantId, UUID id, String moderationState, String note, String actorRef) {
        if (!ModerationState.isKnown(moderationState)) {
            throw new IllegalArgumentException("Unknown moderation state: " + moderationState);
        }
        ContributionEntity c = get(tenantId, id);
        c.setModerationState(moderationState);
        contributionRepository.save(c);
        recordEvent(c, "MODERATED", null, null, actorRef,
                note != null ? note : ("Moderation state set to " + moderationState));
        emit(c, "participation.contribution.moderated");
        return c;
    }

    /** Merge this contribution into a canonical target (marks it MERGED, links merged_into_id). */
    @Transactional
    public ContributionEntity merge(UUID tenantId, UUID id, UUID targetId, String note, String actorRef) {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required to merge");
        }
        if (targetId.equals(id)) {
            throw new IllegalArgumentException("A contribution cannot be merged into itself");
        }
        ContributionEntity target = get(tenantId, targetId); // validates target exists in tenant
        ContributionEntity c = get(tenantId, id);
        if (ContributionLifecycle.MERGED.equals(c.getStatus())) {
            throw new IllegalStateException("Contribution is already merged");
        }
        String from = c.getStatus();
        c.setStatus(ContributionLifecycle.MERGED);
        c.setMergedIntoId(target.getId());
        c.setModerationState(ModerationState.REJECTED); // merged duplicates leave the board
        contributionRepository.save(c);
        recordEvent(c, "MERGED", from, ContributionLifecycle.MERGED, actorRef,
                note != null ? note : ("Merged into " + target.getReference()));
        emit(c, "participation.contribution.merged");
        return c;
    }

    /** Link the contribution to a delivery roadmap item (id reference only). */
    @Transactional
    public ContributionEntity linkRoadmap(UUID tenantId, UUID id, String roadmapRef, String note, String actorRef) {
        ContributionEntity c = get(tenantId, id);
        c.setRoadmapRef(require(roadmapRef, "roadmapRef"));
        contributionRepository.save(c);
        recordEvent(c, "ROADMAP_LINKED", null, null, actorRef,
                note != null ? note : ("Linked to roadmap item " + roadmapRef));
        emit(c, "participation.contribution.roadmap_linked");
        return c;
    }

    // ── internal helpers (also used by IdeaBoardService / PublicContributionIntakeService) ──

    void recordEvent(ContributionEntity c, String eventType, String fromStatus, String toStatus,
                     String actorRef, String note) {
        ContributionEventEntity e = new ContributionEventEntity();
        e.setContributionId(c.getId());
        e.setTenantId(c.getTenantId());
        e.setEventType(eventType);
        e.setFromStatus(fromStatus);
        e.setToStatus(toStatus);
        e.setActorRef(actorRef);
        e.setNote(note);
        eventRepository.save(e);
    }

    void emit(ContributionEntity c, String eventType) {
        emitter.emit("CONTRIBUTION", c.getId().toString(), eventType, "CONTRIBUTION",
                c.getId().toString(), basePayload(c), c.getTenantId());
    }

    private Map<String, Object> basePayload(ContributionEntity c) {
        Map<String, Object> m = new HashMap<>();
        m.put("contributionId", c.getId().toString());
        m.put("reference", c.getReference());
        m.put("type", c.getType());
        m.put("status", c.getStatus());
        m.put("moderationState", c.getModerationState());
        m.put("supportCount", c.getSupportCount());
        return m;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
