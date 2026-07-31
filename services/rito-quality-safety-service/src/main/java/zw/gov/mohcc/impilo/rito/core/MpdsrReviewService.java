package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.domain.CaseClassification;
import zw.gov.mohcc.impilo.rito.domain.CaseLifecycle;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.*;
import zw.gov.mohcc.impilo.rito.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * MPDSR review lifecycle — confidential maternal/perinatal death surveillance in Rito.
 *
 * <p>Consumes {@code DEATH_REVIEW_REQUIRED} (Kafka or HTTP intake) and opens an idempotent
 * {@link CaseClassification#MPDSR_REVIEW} case. Committee, three-delay findings, CAPA actions,
 * and firewalled facility-readiness tasks live here — not in RMNP or the EHR maternity chart.
 */
@Service
public class MpdsrReviewService {

    public static final Set<String> DELAY_TYPES = Set.of(
            "TYPE1_DECIDING_TO_SEEK", "TYPE2_REACHING", "TYPE3_RECEIVING");

    private final MpdsrReviewRepository reviewRepository;
    private final MpdsrCommitteeMeetingRepository meetingRepository;
    private final MpdsrFindingRepository findingRepository;
    private final MpdsrReadinessTaskRepository readinessTaskRepository;
    private final CaseService caseService;
    private final ImprovementService improvementService;
    private final ReferenceGenerator referenceGenerator;
    private final RitoEventEmitter emitter;

    public MpdsrReviewService(MpdsrReviewRepository reviewRepository,
                              MpdsrCommitteeMeetingRepository meetingRepository,
                              MpdsrFindingRepository findingRepository,
                              MpdsrReadinessTaskRepository readinessTaskRepository,
                              CaseService caseService,
                              ImprovementService improvementService,
                              ReferenceGenerator referenceGenerator,
                              RitoEventEmitter emitter) {
        this.reviewRepository = reviewRepository;
        this.meetingRepository = meetingRepository;
        this.findingRepository = findingRepository;
        this.readinessTaskRepository = readinessTaskRepository;
        this.caseService = caseService;
        this.improvementService = improvementService;
        this.referenceGenerator = referenceGenerator;
        this.emitter = emitter;
    }

    public record DeathEventIntake(
            UUID deathCaseId,
            UUID facilityId,
            UUID districtId,
            String triggerFlags,
            String nearMissRef,
            String whoClassification,
            String reviewLevel) {
    }

    /**
     * Idempotent open keyed by PCT death case id. Returns existing review if already opened.
     */
    @Transactional
    public MpdsrReviewEntity openFromDeathEvent(UUID tenantId, DeathEventIntake intake, String actorId) {
        UUID deathCaseId = requireUuid(intake.deathCaseId(), "deathCaseId");
        return reviewRepository.findByTenantIdAndDeathCaseId(tenantId, deathCaseId)
                .orElseGet(() -> createReview(tenantId, intake, actorId));
    }

    private MpdsrReviewEntity createReview(UUID tenantId, DeathEventIntake intake, String actorId) {
        String flags = intake.triggerFlags();
        String level = intake.reviewLevel() != null ? intake.reviewLevel() : "FACILITY";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deathCaseId", intake.deathCaseId().toString());
        metadata.put("mpdsr", true);

        CaseEntity caseEntity = caseService.createCase(tenantId, new CaseService.NewCase(
                CaseClassification.MPDSR_REVIEW,
                "MPDSR review — death case " + intake.deathCaseId(),
                "Confidential maternal/perinatal death surveillance review opened from DEATH_REVIEW_REQUIRED.",
                "HIGH",
                "INTEGRATION_API",
                "MPDSR",
                true,
                intake.facilityId(),
                intake.districtId(),
                null,
                null,
                actorId != null ? actorId : "pct-service",
                "SERVICE",
                null,
                false,
                metadata));

        caseService.addLink(tenantId, caseEntity.getId(), "VISIT", intake.deathCaseId().toString(), "PCT",
                "Death case trigger for MPDSR review");

        MpdsrReviewEntity review = new MpdsrReviewEntity();
        review.setTenantId(tenantId);
        review.setCaseId(caseEntity.getId());
        review.setDeathCaseId(intake.deathCaseId());
        review.setNearMissRef(intake.nearMissRef());
        review.setWhoClassification(intake.whoClassification());
        review.setReviewLevel(level);
        review.setStatus("OPEN");
        review.setFacilityId(intake.facilityId());
        review.setDistrictId(intake.districtId());
        review.setTriggerFlags(flags);
        reviewRepository.save(review);

        emitReviewEvent(review, "rito.mpdsr.review.opened");
        return review;
    }

    @Transactional(readOnly = true)
    public MpdsrReviewEntity get(UUID tenantId, UUID reviewId) {
        return loadReview(tenantId, reviewId);
    }

    @Transactional(readOnly = true)
    public List<MpdsrReviewEntity> list(UUID tenantId, String status) {
        if (status == null || status.isBlank()) {
            return reviewRepository.findByTenantIdOrderByOpenedAtDesc(tenantId);
        }
        return reviewRepository.findByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, status);
    }

    @Transactional
    public MpdsrCommitteeMeetingEntity recordCommitteeMeeting(UUID tenantId, UUID reviewId,
            OffsetDateTime meetingAt, String reviewLevel, String chairActorId,
            String membersJson, String minutesSummary) {
        MpdsrReviewEntity review = loadReview(tenantId, reviewId);
        MpdsrCommitteeMeetingEntity meeting = new MpdsrCommitteeMeetingEntity();
        meeting.setTenantId(tenantId);
        meeting.setReviewId(review.getId());
        meeting.setMeetingAt(meetingAt != null ? meetingAt : OffsetDateTime.now());
        meeting.setReviewLevel(reviewLevel != null ? reviewLevel : review.getReviewLevel());
        meeting.setChairActorId(chairActorId);
        meeting.setMembersJson(membersJson != null ? membersJson : "[]");
        meeting.setMinutesSummary(minutesSummary);
        return meetingRepository.save(meeting);
    }

    @Transactional
    public MpdsrFindingEntity addFinding(UUID tenantId, UUID reviewId, String delayType,
            String modifiableFactor, String narrative) {
        loadReview(tenantId, reviewId);
        if (delayType == null || !DELAY_TYPES.contains(delayType)) {
            throw new IllegalArgumentException("delayType must be one of " + DELAY_TYPES);
        }
        MpdsrFindingEntity finding = new MpdsrFindingEntity();
        finding.setTenantId(tenantId);
        finding.setReviewId(reviewId);
        finding.setDelayType(delayType);
        finding.setModifiableFactor(require(modifiableFactor, "modifiableFactor"));
        finding.setNarrative(narrative);
        return findingRepository.save(finding);
    }

    @Transactional
    public CorrectiveActionEntity addAction(UUID tenantId, UUID reviewId, String title, String description,
            String ownerActorId, String ownerActorType, OffsetDateTime dueAt, String createdBy) {
        MpdsrReviewEntity review = loadReview(tenantId, reviewId);
        return improvementService.createCorrectiveAction(
                tenantId, "CORRECTIVE", require(title, "title"), description,
                "MPDSR_REVIEW", reviewId.toString(),
                review.getCaseId(), null, review.getFacilityId(),
                ownerActorId, ownerActorType, "HIGH", dueAt, createdBy);
    }

    /**
     * Raise a facility-readiness reassessment task. Outbound payload is firewalled — no review content.
     */
    @Transactional
    public MpdsrReadinessTaskEntity raiseFacilityReadinessTask(UUID tenantId, UUID reviewId,
            OffsetDateTime dueAt) {
        MpdsrReviewEntity review = loadReview(tenantId, reviewId);
        if (review.getFacilityId() == null) {
            throw new IllegalArgumentException("Review has no facility — cannot raise readiness task");
        }

        String dueIso = dueAt != null ? dueAt.toString() : null;
        Map<String, Object> payload = MpdsrReadinessFirewall.buildSanitizedPayload(
                review.getFacilityId().toString(), dueIso);

        MpdsrReadinessTaskEntity task = new MpdsrReadinessTaskEntity();
        task.setTenantId(tenantId);
        task.setReviewId(reviewId);
        task.setFacilityId(review.getFacilityId());
        task.setTaskReference(referenceGenerator.generate("RITO-MRT",
                ref -> readinessTaskRepository.existsByTenantIdAndTaskReference(tenantId, ref)));
        task.setPayloadJson(JsonUtil.toJson(payload));
        task.setStatus("OPEN");
        task.setDueAt(dueAt);
        readinessTaskRepository.save(task);

        Map<String, Object> eventPayload = new HashMap<>(payload);
        eventPayload.put("taskReference", task.getTaskReference());
        emitter.emit("MPDSR_READINESS_TASK", task.getId().toString(),
                "rito.mpdsr.readiness_task.requested", "FACILITY",
                review.getFacilityId().toString(), eventPayload, tenantId);
        return task;
    }

    @Transactional
    public MpdsrReviewEntity closeReview(UUID tenantId, UUID reviewId, String actorId, String actorType) {
        MpdsrReviewEntity review = loadReview(tenantId, reviewId);
        review.setStatus("CLOSED");
        review.setClosedAt(OffsetDateTime.now());
        reviewRepository.save(review);

        String actor = actorId != null ? actorId : "qi-officer";
        String type = actorType != null ? actorType : "OPERATOR";
        CaseEntity c = caseService.get(tenantId, review.getCaseId(), actor, type);
        advanceCaseToClosed(tenantId, c.getId(), actor, type);

        emitReviewEvent(review, "rito.mpdsr.review.closed");
        return review;
    }

    /** Walk the case lifecycle to CLOSED from whatever open state it is in. */
    private void advanceCaseToClosed(UUID tenantId, UUID caseId, String actorId, String actorType) {
        CaseEntity c = caseService.get(tenantId, caseId, actorId, actorType);
        String status = c.getStatus();
        if (CaseLifecycle.CLOSED.equals(status) || CaseLifecycle.CANCELLED.equals(status)) {
            return;
        }
        if (CaseLifecycle.SUBMITTED.equals(status) || CaseLifecycle.REOPENED.equals(status)) {
            caseService.acknowledge(tenantId, caseId, actorId, actorType);
        }
        c = caseService.get(tenantId, caseId, actorId, actorType);
        if (CaseLifecycle.ACKNOWLEDGED.equals(c.getStatus())) {
            caseService.triage(tenantId, caseId, c.getSeverity(), c.getCategory(),
                    null, null, null, actorId, actorType);
        }
        c = caseService.get(tenantId, caseId, actorId, actorType);
        if (CaseLifecycle.TRIAGED.equals(c.getStatus())) {
            caseService.assign(tenantId, caseId, actorId, actorType, "MPDSR_COMMITTEE",
                    actorId, actorType, "MPDSR review closure");
        }
        c = caseService.get(tenantId, caseId, actorId, actorType);
        if (CaseLifecycle.ASSIGNED.equals(c.getStatus())) {
            caseService.transition(tenantId, caseId, CaseLifecycle.UNDER_REVIEW,
                    "MPDSR committee review complete", actorId, actorType);
        }
        c = caseService.get(tenantId, caseId, actorId, actorType);
        if (!CaseLifecycle.RESOLVED.equals(c.getStatus()) && !CaseLifecycle.CLOSED.equals(c.getStatus())) {
            caseService.resolve(tenantId, caseId, "MPDSR review closed", "COMPLETED", actorId, actorType);
        }
        c = caseService.get(tenantId, caseId, actorId, actorType);
        if (CaseLifecycle.RESOLVED.equals(c.getStatus())) {
            caseService.close(tenantId, caseId, null, actorId, actorType);
        }
    }

    @Transactional(readOnly = true)
    public List<MpdsrCommitteeMeetingEntity> meetings(UUID reviewId) {
        return meetingRepository.findByReviewIdOrderByMeetingAtDesc(reviewId);
    }

    @Transactional(readOnly = true)
    public List<MpdsrFindingEntity> findings(UUID reviewId) {
        return findingRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
    }

    @Transactional(readOnly = true)
    public List<MpdsrReadinessTaskEntity> readinessTasks(UUID reviewId) {
        return readinessTaskRepository.findByReviewIdOrderByCreatedAtDesc(reviewId);
    }

    @Transactional(readOnly = true)
    public List<CorrectiveActionEntity> actions(UUID tenantId, UUID reviewId) {
        MpdsrReviewEntity review = loadReview(tenantId, reviewId);
        return improvementService.byCase(tenantId, review.getCaseId());
    }

    private void emitReviewEvent(MpdsrReviewEntity review, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reviewId", review.getId().toString());
        payload.put("caseId", review.getCaseId().toString());
        payload.put("deathCaseId", review.getDeathCaseId().toString());
        payload.put("status", review.getStatus());
        payload.put("reviewLevel", review.getReviewLevel());
        emitter.emit("MPDSR_REVIEW", review.getId().toString(), eventType, "MPDSR_REVIEW",
                review.getId().toString(), payload, review.getTenantId());
    }

    private MpdsrReviewEntity loadReview(UUID tenantId, UUID reviewId) {
        return reviewRepository.findByIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("MPDSR review not found: " + reviewId));
    }

    private static UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
