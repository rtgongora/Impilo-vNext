package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.integration.VarapiClient;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAuditEventEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PicNominationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PractitionerInChargeAssignmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAuditEventRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PicNominationRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Practitioner-in-Charge nomination workflow (HPA-2017-V1 state machine 3.5).
 *
 * <p>{@code PROPOSED → ELIGIBILITY_CHECKED → PRACTITIONER_ACCEPTANCE_PENDING →
 * ACCEPTED/DECLINED/DISPUTED → REGULATOR_REVIEW_PENDING → APPROVED/REJECTED →
 * ACTIVATED}. Tuso owns the facility-effective assignment; VARAPI supplies the
 * point-in-time eligibility assessment (snapshotted verbatim on the nomination)
 * and remains the professional registry. Invariants enforced here:</p>
 * <ul>
 *   <li>No free-text practitioner names — nominations resolve a Provider ID
 *       through VARAPI or fail.</li>
 *   <li>No activation without practitioner acceptance and recorded
 *       regulator/council review.</li>
 *   <li>No destructive replacement — activation end-dates the predecessor
 *       historically and links it.</li>
 *   <li>Credential changes never delete an assignment; they flag it
 *       REVIEW_REQUIRED for an audited human decision.</li>
 * </ul>
 */
@Service
public class PicNominationService {

    private static final Logger log = LoggerFactory.getLogger(PicNominationService.class);

    private static final Set<String> NOMINATOR_ROLES = Set.of(
            "FACILITY_APPLICANT", "FACILITY_MANAGER", "FACILITY_ADMIN", "HPA_REGISTRAR", "SYSTEM_ADMIN", "DEVELOPER");
    private static final Set<String> REVIEWER_ROLES = Set.of(
            "HPA_REGISTRAR", "HPA_ADMIN", "COUNCIL_REVIEWER", "SYSTEM_ADMIN", "DEVELOPER");

    private final PicNominationRepository nominationRepository;
    private final PractitionerInChargeAssignmentRepository assignmentRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityAuditEventRepository auditEventRepository;
    private final EventOutboxRepository outboxRepository;
    private final VarapiClient varapiClient;

    public PicNominationService(PicNominationRepository nominationRepository,
                                PractitionerInChargeAssignmentRepository assignmentRepository,
                                FacilityRepository facilityRepository,
                                FacilityAuditEventRepository auditEventRepository,
                                EventOutboxRepository outboxRepository,
                                VarapiClient varapiClient) {
        this.nominationRepository = nominationRepository;
        this.assignmentRepository = assignmentRepository;
        this.facilityRepository = facilityRepository;
        this.auditEventRepository = auditEventRepository;
        this.outboxRepository = outboxRepository;
        this.varapiClient = varapiClient;
    }

    public record NominateRequest(Long facilityId, Long facilityUnitId, String providerPublicId,
                                  UUID linkedApplicationId, String notes) {}

    /** Step 1-2: nominate by Provider ID; VARAPI resolves + assesses; snapshot stored verbatim. */
    @Transactional
    public PicNominationEntity nominate(NominateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, NOMINATOR_ROLES, "nominate a practitioner in charge");
        if (request.providerPublicId() == null || request.providerPublicId().isBlank()) {
            throw new IllegalArgumentException("providerPublicId is required — free-text PIC names are not accepted");
        }
        FacilityEntity facility = facilityRepository.findById(request.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + request.facilityId()));
        if (!facility.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }

        // VARAPI must resolve the practitioner; an unresolvable provider fails the nomination.
        Map<String, Object> assessment = varapiClient.requestEligibilityAssessment(
                request.providerPublicId().trim(),
                String.valueOf(facility.getId()),
                request.facilityUnitId() != null ? String.valueOf(request.facilityUnitId()) : null,
                "PIC_NOMINATION",
                ctx.tenantId().toString());

        PicNominationEntity nomination = new PicNominationEntity();
        nomination.setTenantId(ctx.tenantId());
        nomination.setFacilityId(facility.getId());
        nomination.setFacilityUnitId(request.facilityUnitId());
        nomination.setProviderPublicId(request.providerPublicId().trim());
        nomination.setImpiloHealthId(uuidOrNull(assessment.get("impiloHealthId")));
        nomination.setEligibilitySnapshot(assessment);
        nomination.setEligibilityResult(stringOr(assessment.get("result"), "UNKNOWN"));
        nomination.setEligibilitySnapshotRef(stringOr(assessment.get("snapshotId"), null));
        nomination.setState("INELIGIBLE".equals(nomination.getEligibilityResult())
                ? "ELIGIBILITY_CHECKED"
                : "PRACTITIONER_ACCEPTANCE_PENDING");
        nomination.setNominatedBy(ctx.actorId());
        nomination.setLinkedApplicationId(request.linkedApplicationId());
        nomination.setNotes(request.notes());
        nomination.setUpdatedBy(ctx.actorId());
        nomination = nominationRepository.save(nomination);

        audit(ctx, facility.getId(), "PIC_NOMINATED", nomination.getNominationId(),
                Map.of("providerPublicId", nomination.getProviderPublicId(),
                        "eligibilityResult", nomination.getEligibilityResult(),
                        "state", nomination.getState()));
        publishEvent(ctx, facility.getId(), "tuso.facility.pic.nominated", Map.of(
                "nominationId", nomination.getNominationId().toString(),
                "facilityId", facility.getId(),
                "providerPublicId", nomination.getProviderPublicId(),
                "impiloHealthId", nomination.getImpiloHealthId() != null ? nomination.getImpiloHealthId().toString() : "",
                "eligibilityResult", nomination.getEligibilityResult()));
        log.info("PIC nomination {} created for facility {} provider {} (result={})",
                nomination.getNominationId(), facility.getId(), nomination.getProviderPublicId(),
                nomination.getEligibilityResult());
        return nomination;
    }

    /** Step 8: the practitioner accepts, declines or disputes — only the nominated practitioner. */
    @Transactional
    public PicNominationEntity respond(UUID nominationId, String response, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        PicNominationEntity nomination = require(nominationId, ctx);
        requireState(nomination, "PRACTITIONER_ACCEPTANCE_PENDING");

        // The responder must BE the nominated practitioner (health-id anchor match).
        if (nomination.getImpiloHealthId() == null
                || ctx.actorId() == null
                || !ctx.actorId().equalsIgnoreCase(nomination.getImpiloHealthId().toString())) {
            throw new SecurityException("Only the nominated practitioner may respond to this nomination");
        }

        String verdict = switch (response == null ? "" : response.toUpperCase()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "DECLINED" -> "DECLINED";
            case "DISPUTED" -> "DISPUTED";
            default -> throw new IllegalArgumentException("Response must be ACCEPTED, DECLINED or DISPUTED");
        };
        nomination.setPractitionerResponse(verdict);
        nomination.setPractitionerResponseAt(Instant.now());
        nomination.setPractitionerResponseNotes(notes);
        nomination.setState("ACCEPTED".equals(verdict) ? "REGULATOR_REVIEW_PENDING" : verdict);
        nomination.setUpdatedBy(ctx.actorId());
        nomination = nominationRepository.save(nomination);

        audit(ctx, nomination.getFacilityId(), "PIC_NOMINATION_RESPONSE", nominationId,
                Map.of("response", verdict, "state", nomination.getState()));
        publishEvent(ctx, nomination.getFacilityId(),
                "ACCEPTED".equals(verdict) ? "tuso.facility.pic.accepted" : "tuso.facility.pic.declined",
                Map.of("nominationId", nominationId.toString(),
                        "facilityId", nomination.getFacilityId(),
                        "providerPublicId", nomination.getProviderPublicId(),
                        "response", verdict));
        return nomination;
    }

    /** Step 9: council/HPA review recorded by an authorised reviewer — never automatic. */
    @Transactional
    public PicNominationEntity recordReview(UUID nominationId, String authority, String reference,
                                            boolean approved, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, REVIEWER_ROLES, "record a PIC regulatory review");
        PicNominationEntity nomination = require(nominationId, ctx);
        requireState(nomination, "REGULATOR_REVIEW_PENDING");

        nomination.setReviewAuthority(authority);
        nomination.setReviewReference(reference);
        nomination.setReviewState(approved ? "APPROVED" : "REJECTED");
        nomination.setReviewRecordedBy(ctx.actorId());
        nomination.setReviewRecordedAt(Instant.now());
        nomination.setState(approved ? "APPROVED" : "REJECTED");
        if (notes != null && !notes.isBlank()) {
            nomination.setNotes(nomination.getNotes() == null ? notes : nomination.getNotes() + "\n" + notes);
        }
        nomination.setUpdatedBy(ctx.actorId());
        nomination = nominationRepository.save(nomination);

        audit(ctx, nomination.getFacilityId(), "PIC_REVIEW_RECORDED", nominationId,
                Map.of("authority", stringOr(authority, ""), "approved", approved));
        publishEvent(ctx, nomination.getFacilityId(), "tuso.facility.pic.review_recorded", Map.of(
                "nominationId", nominationId.toString(),
                "facilityId", nomination.getFacilityId(),
                "approved", approved,
                "authority", stringOr(authority, "")));
        return nomination;
    }

    /** Steps 10-11: effective-dated activation; predecessor ends historically, never overwritten. */
    @Transactional
    public PicNominationEntity activate(UUID nominationId) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, REVIEWER_ROLES, "activate a PIC assignment");
        PicNominationEntity nomination = require(nominationId, ctx);
        requireState(nomination, "APPROVED");

        LocalDate today = LocalDate.now();
        Long predecessorId = null;
        List<PractitionerInChargeAssignmentEntity> current =
                assignmentRepository.findByFacilityIdAndEndDateIsNull(nomination.getFacilityId());
        for (PractitionerInChargeAssignmentEntity previous : current) {
            boolean sameScope = nomination.getFacilityUnitId() == null
                    ? previous.getFacilityUnitId() == null
                    : nomination.getFacilityUnitId().equals(previous.getFacilityUnitId());
            if (sameScope) {
                previous.setEndDate(today);
                previous.setUpdatedBy(ctx.actorId());
                assignmentRepository.save(previous);
                predecessorId = previous.getId();
            }
        }

        Long targetFacilityId = nomination.getFacilityId();
        FacilityEntity facility = facilityRepository.findById(targetFacilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + targetFacilityId));

        PractitionerInChargeAssignmentEntity assignment = new PractitionerInChargeAssignmentEntity();
        assignment.setFacility(facility);
        assignment.setFacilityUnitId(nomination.getFacilityUnitId());
        assignment.setProviderPublicId(nomination.getProviderPublicId());
        assignment.setImpiloHealthId(nomination.getImpiloHealthId());
        assignment.setRole("PRACTITIONER_IN_CHARGE");
        assignment.setStartDate(today);
        assignment.setApprovalState("APPROVED");
        assignment.setSourceCouncilReference(nomination.getReviewReference());
        assignment.setPredecessorAssignmentId(predecessorId);
        assignment.setReviewState("IN_GOOD_STANDING");
        assignment.setNotes("Activated from nomination " + nomination.getNominationId());
        assignment.setCreatedBy(ctx.actorId());
        assignment.setUpdatedBy(ctx.actorId());
        assignment = assignmentRepository.save(assignment);

        nomination.setState("ACTIVATED");
        nomination.setActivatedAssignmentId(assignment.getId());
        nomination.setUpdatedBy(ctx.actorId());
        nomination = nominationRepository.save(nomination);

        audit(ctx, nomination.getFacilityId(), "PIC_ASSIGNMENT_ACTIVATED", nominationId,
                Map.of("assignmentId", assignment.getId(),
                        "predecessorAssignmentId", predecessorId == null ? "" : predecessorId,
                        "providerPublicId", nomination.getProviderPublicId()));
        publishEvent(ctx, nomination.getFacilityId(), "tuso.facility.pic.activated", Map.of(
                "nominationId", nominationId.toString(),
                "facilityId", nomination.getFacilityId(),
                "assignmentId", assignment.getId(),
                "providerPublicId", nomination.getProviderPublicId(),
                "effectiveFrom", today.toString()));
        log.info("PIC assignment {} activated for facility {} (nomination {}, predecessor {})",
                assignment.getId(), nomination.getFacilityId(), nominationId, predecessorId);
        return nomination;
    }

    /** Nominator/registrar may withdraw a nomination before activation. */
    @Transactional
    public PicNominationEntity withdraw(UUID nominationId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, NOMINATOR_ROLES, "withdraw a PIC nomination");
        PicNominationEntity nomination = require(nominationId, ctx);
        if ("ACTIVATED".equals(nomination.getState())) {
            throw new IllegalStateException("An activated assignment cannot be withdrawn — raise a PIC change instead");
        }
        nomination.setState("WITHDRAWN");
        nomination.setNotes(reason);
        nomination.setUpdatedBy(ctx.actorId());
        nomination = nominationRepository.save(nomination);
        audit(ctx, nomination.getFacilityId(), "PIC_NOMINATION_WITHDRAWN", nominationId,
                Map.of("reason", stringOr(reason, "")));
        return nomination;
    }

    /**
     * VARAPI credential-change hook (Kafka consumer path): flags ACTIVE
     * assignments for review. Never deletes; never closes the facility.
     */
    @Transactional
    public int flagAssignmentsForReview(String providerPublicId, String reason, UUID tenantId) {
        List<PractitionerInChargeAssignmentEntity> active =
                assignmentRepository.findByProviderPublicIdAndEndDateIsNull(providerPublicId);
        int flagged = 0;
        for (PractitionerInChargeAssignmentEntity assignment : active) {
            assignment.setReviewState("REVIEW_REQUIRED");
            assignment.setReviewReason(reason);
            assignmentRepository.save(assignment);
            flagged++;

            FacilityAuditEventEntity audit = new FacilityAuditEventEntity();
            audit.setTenantId(tenantId);
            audit.setFacility(assignment.getFacility());
            audit.setActorId("varapi-credential-consumer");
            audit.setActorType("SERVICE");
            audit.setAction("PIC_ELIGIBILITY_REVIEW_REQUIRED");
            audit.setTargetEntityType("PIC_ASSIGNMENT");
            audit.setTargetEntityId(String.valueOf(assignment.getId()));
            audit.setAfterState(Map.of("reviewState", "REVIEW_REQUIRED", "reason", stringOr(reason, "")));
            auditEventRepository.save(audit);

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("FACILITY");
            event.setAggregateId(String.valueOf(assignment.getFacility().getId()));
            event.setEventType("tuso.facility.pic.review_required");
            event.setPayload(Map.of(
                    "facilityId", assignment.getFacility().getId(),
                    "assignmentId", assignment.getId(),
                    "providerPublicId", providerPublicId,
                    "reason", stringOr(reason, "")));
            event.setTenantId(tenantId != null ? tenantId.toString() : null);
            event.setPodId("national-spine");
            event.setIdempotencyKey("tuso:pic-review:" + assignment.getId() + ":" + UUID.randomUUID());
            outboxRepository.save(event);
        }
        if (flagged > 0) {
            log.warn("Flagged {} active PIC assignment(s) for review — provider {} credential change: {}",
                    flagged, providerPublicId, reason);
        }
        return flagged;
    }

    /** Audited human clearance of a credential-review flag. */
    @Transactional
    public PractitionerInChargeAssignmentEntity resolveReview(Long assignmentId, boolean cleared, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, REVIEWER_ROLES, "resolve a PIC credential review");
        PractitionerInChargeAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        assignment.setReviewState(cleared ? "CLEARED" : "UNDER_REVIEW");
        assignment.setReviewReason(notes);
        assignment.setUpdatedBy(ctx.actorId());
        assignment = assignmentRepository.save(assignment);
        audit(ctx, assignment.getFacility().getId(), "PIC_REVIEW_RESOLVED", null,
                Map.of("assignmentId", assignmentId, "cleared", cleared, "notes", stringOr(notes, "")));
        return assignment;
    }

    @Transactional(readOnly = true)
    public List<PicNominationEntity> listForFacility(Long facilityId) {
        return nominationRepository.findByFacilityIdOrderByNominatedAtDesc(facilityId);
    }

    /** The practitioner's own view — pending + historical nominations by health-id anchor. */
    @Transactional(readOnly = true)
    public List<PicNominationEntity> listForPractitioner(String providerPublicId) {
        return nominationRepository.findByProviderPublicIdOrderByNominatedAtDesc(providerPublicId);
    }

    // ---- helpers ---------------------------------------------------------

    private PicNominationEntity require(UUID nominationId, TrustContext ctx) {
        PicNominationEntity nomination = nominationRepository.findById(nominationId)
                .orElseThrow(() -> new IllegalArgumentException("Nomination not found: " + nominationId));
        if (!nomination.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return nomination;
    }

    private static void requireState(PicNominationEntity nomination, String expected) {
        if (!expected.equals(nomination.getState())) {
            throw new IllegalStateException("Nomination is in state " + nomination.getState()
                    + " — expected " + expected);
        }
    }

    private static void assertRole(TrustContext ctx, Set<String> allowed, String action) {
        String actorType = ctx.actorType();
        if (actorType == null || actorType.isBlank()) {
            return; // internal/service calls carry no actor type — consistent with engine convention
        }
        if (!allowed.contains(actorType) && !"PROVIDER".equals(actorType) && !"OPERATOR".equals(actorType)) {
            throw new SecurityException("Actor type " + actorType + " cannot " + action);
        }
    }

    private void audit(TrustContext ctx, Long facilityId, String action, UUID targetId, Map<String, Object> after) {
        FacilityAuditEventEntity audit = new FacilityAuditEventEntity();
        audit.setTenantId(ctx.tenantId());
        facilityRepository.findById(facilityId).ifPresent(audit::setFacility);
        audit.setActorId(ctx.actorId());
        audit.setActorType(ctx.actorType());
        audit.setAction(action);
        audit.setTargetEntityType("PIC_NOMINATION");
        audit.setTargetEntityId(targetId != null ? targetId.toString() : "");
        audit.setAfterState(after);
        audit.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        auditEventRepository.save(audit);
    }

    private void publishEvent(TrustContext ctx, Long facilityId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY");
        event.setAggregateId(String.valueOf(facilityId));
        event.setEventType(eventType);
        event.setPayload(new LinkedHashMap<>(payload));
        event.setTenantId(ctx.tenantId().toString());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setProducer("tuso");
        event.setSubjectType("Facility");
        event.setSubjectId(String.valueOf(facilityId));
        event.setPartitionKey(String.valueOf(facilityId));
        event.setSchemaVersion(1);
        event.setPodId("national-spine");
        event.setIdempotencyKey("tuso:pic:" + eventType + ":" + UUID.randomUUID());
        outboxRepository.save(event);
    }

    private static String stringOr(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static UUID uuidOrNull(Object value) {
        try {
            return value instanceof String s && !s.isBlank() ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
