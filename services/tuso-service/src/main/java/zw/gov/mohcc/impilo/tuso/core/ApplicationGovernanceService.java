package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ApplicationInformationRequestEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ExternalCouncilReviewEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationState;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryApplicationTypeEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ApplicationInformationRequestRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ExternalCouncilReviewRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityApplicationRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryApplicationTypeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Application governance companions: the request-for-information cycle and the
 * external professional-council review (HPA-2017-V1 pp.6-7, 10). Council review
 * is a REAL external record — reviewing authority, reference, dates, status,
 * conditions, evidence, recorded-by — never a boolean and never auto-approved.
 */
@Service
public class ApplicationGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationGovernanceService.class);

    /**
     * Regulator-side acts on an application: opening, closing an information request, recording a
     * council review.
     *
     * <p>Was {@code {HPA_REGISTRAR, HPA_ADMIN, COUNCIL_REVIEWER, SYSTEM_ADMIN, DEVELOPER}} — none
     * emittable — and {@code assertRole} returned early when the actor type was absent, so the only
     * way through was to send no X-Actor-Type header. Now fails closed.</p>
     */
    private static final ActorTypeGuard.Duty ACT_AS_REGULATOR = new ActorTypeGuard.Duty(
            "acting as regulator on a facility application",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            Set.of("HPA_REGISTRAR", "HPA_ADMIN", "COUNCIL_REVIEWER", "SYSTEM_ADMIN", "DEVELOPER"));

    /**
     * Applicant-side acts: responding to an information request.
     *
     * <p>Enforces {@code PROVIDER} in addition to the back-office set, because a practitioner
     * applying for their own practice is a real applicant and is the one actor type that would
     * otherwise be locked out of their own application.</p>
     */
    private static final ActorTypeGuard.Duty ACT_AS_APPLICANT = new ActorTypeGuard.Duty(
            "responding as the applicant on a facility application",
            Set.of("SYSTEM", "SERVICE", "OPERATOR", "PROVIDER"),
            Set.of("FACILITY_APPLICANT", "FACILITY_MANAGER", "FACILITY_ADMIN", "HPA_REGISTRAR",
                    "SYSTEM_ADMIN", "DEVELOPER"));

    private final ApplicationInformationRequestRepository rfiRepository;
    private final ExternalCouncilReviewRepository councilReviewRepository;
    private final FacilityApplicationRepository applicationRepository;
    private final RegulatoryApplicationTypeRepository applicationTypeRepository;
    private final EventOutboxRepository outboxRepository;

    public ApplicationGovernanceService(ApplicationInformationRequestRepository rfiRepository,
                                        ExternalCouncilReviewRepository councilReviewRepository,
                                        FacilityApplicationRepository applicationRepository,
                                        RegulatoryApplicationTypeRepository applicationTypeRepository,
                                        EventOutboxRepository outboxRepository) {
        this.rfiRepository = rfiRepository;
        this.councilReviewRepository = councilReviewRepository;
        this.applicationRepository = applicationRepository;
        this.applicationTypeRepository = applicationTypeRepository;
        this.outboxRepository = outboxRepository;
    }

    // ---- Catalogue -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RegulatoryApplicationTypeEntity> listApplicationTypes() {
        return applicationTypeRepository.findByActiveTrue();
    }

    // ---- RFI cycle -------------------------------------------------------

    @Transactional
    public ApplicationInformationRequestEntity openInformationRequest(UUID applicationId, String message,
                                                                      LocalDate dueDate) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, ACT_AS_REGULATOR);
        FacilityApplicationEntity application = requireApplication(applicationId, ctx);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("An information request must say what is required");
        }

        ApplicationInformationRequestEntity rfi = new ApplicationInformationRequestEntity();
        rfi.setTenantId(ctx.tenantId());
        rfi.setApplicationId(applicationId);
        rfi.setRequestedBy(ctx.actorId());
        rfi.setMessage(message);
        rfi.setDueDate(dueDate);
        rfi.setStatus("OPEN");
        rfi = rfiRepository.save(rfi);

        application.setCurrentWorkflowState(FacilityApplicationState.AWAITING_DOCUMENTS);
        application.setUpdatedBy(ctx.actorId());
        applicationRepository.save(application);

        publishEvent(ctx, application, "tuso.facility.application.information_requested", Map.of(
                "applicationId", applicationId.toString(),
                "rfiId", rfi.getRfiId().toString(),
                "dueDate", dueDate != null ? dueDate.toString() : ""));
        log.info("RFI {} opened on application {}", rfi.getRfiId(), applicationId);
        return rfi;
    }

    @Transactional
    public ApplicationInformationRequestEntity respondToInformationRequest(UUID rfiId, String responseNotes,
                                                                           UUID responseDocumentId) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, ACT_AS_APPLICANT);
        ApplicationInformationRequestEntity rfi = requireRfi(rfiId, ctx);
        if (!"OPEN".equals(rfi.getStatus())) {
            throw new IllegalStateException("Information request is " + rfi.getStatus() + " — no response expected");
        }
        rfi.setStatus("RESPONDED");
        rfi.setResponseNotes(responseNotes);
        rfi.setResponseDocumentId(responseDocumentId);
        rfi.setRespondedAt(Instant.now());
        rfi = rfiRepository.save(rfi);

        FacilityApplicationEntity application = requireApplication(rfi.getApplicationId(), ctx);
        publishEvent(ctx, application, "tuso.facility.application.information_provided", Map.of(
                "applicationId", rfi.getApplicationId().toString(),
                "rfiId", rfiId.toString()));
        return rfi;
    }

    @Transactional
    public ApplicationInformationRequestEntity closeInformationRequest(UUID rfiId, boolean satisfied) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, ACT_AS_REGULATOR);
        ApplicationInformationRequestEntity rfi = requireRfi(rfiId, ctx);
        rfi.setStatus("CLOSED");
        rfi.setClosedBy(ctx.actorId());
        rfi.setClosedAt(Instant.now());
        rfi = rfiRepository.save(rfi);

        // Return the application to review once no RFIs remain open.
        if (rfiRepository.countByApplicationIdAndStatus(rfi.getApplicationId(), "OPEN") == 0) {
            FacilityApplicationEntity application = requireApplication(rfi.getApplicationId(), ctx);
            if (application.getCurrentWorkflowState() == FacilityApplicationState.AWAITING_DOCUMENTS) {
                application.setCurrentWorkflowState(FacilityApplicationState.SUBMITTED);
                application.setUpdatedBy(ctx.actorId());
                applicationRepository.save(application);
            }
        }
        return rfi;
    }

    @Transactional(readOnly = true)
    public List<ApplicationInformationRequestEntity> listInformationRequests(UUID applicationId) {
        return rfiRepository.findByApplicationIdOrderByRequestedAtDesc(applicationId);
    }

    // ---- External council review ------------------------------------------

    public record RecordCouncilReviewRequest(UUID applicationId, String reviewingAuthority,
                                             String referenceNumber, LocalDate submittedDate,
                                             LocalDate decisionDate, String status,
                                             String conditions, String notes,
                                             UUID evidenceDocumentId) {}

    @Transactional
    public ExternalCouncilReviewEntity recordCouncilReview(RecordCouncilReviewRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertRole(ctx, ACT_AS_REGULATOR);
        FacilityApplicationEntity application = requireApplication(request.applicationId(), ctx);
        if (request.reviewingAuthority() == null || request.reviewingAuthority().isBlank()) {
            throw new IllegalArgumentException("reviewingAuthority (canonical council code) is required");
        }
        String status = request.status() != null ? request.status().toUpperCase() : "PENDING";
        if (!Set.of("PENDING", "ENDORSED", "REJECTED", "NOT_REQUIRED").contains(status)) {
            throw new IllegalArgumentException("Council review status must be PENDING, ENDORSED, REJECTED or NOT_REQUIRED");
        }

        ExternalCouncilReviewEntity review = new ExternalCouncilReviewEntity();
        review.setTenantId(ctx.tenantId());
        review.setApplicationId(request.applicationId());
        review.setReviewingAuthority(request.reviewingAuthority());
        review.setReferenceNumber(request.referenceNumber());
        review.setSubmittedDate(request.submittedDate());
        review.setDecisionDate(request.decisionDate());
        review.setStatus(status);
        review.setConditions(request.conditions());
        review.setNotes(request.notes());
        review.setEvidenceDocumentId(request.evidenceDocumentId());
        review.setRecordedBy(ctx.actorId());
        review = councilReviewRepository.save(review);

        // A PENDING council record parks the application; endorsement returns it.
        if ("PENDING".equals(status)) {
            application.setCurrentWorkflowState(FacilityApplicationState.AWAITING_COUNCIL_REVIEW);
        } else if (application.getCurrentWorkflowState() == FacilityApplicationState.AWAITING_COUNCIL_REVIEW) {
            application.setCurrentWorkflowState(FacilityApplicationState.SUBMITTED);
        }
        application.setUpdatedBy(ctx.actorId());
        applicationRepository.save(application);

        publishEvent(ctx, application, "tuso.facility.council_review.recorded", Map.of(
                "applicationId", request.applicationId().toString(),
                "reviewId", review.getReviewId().toString(),
                "authority", request.reviewingAuthority(),
                "status", status));
        log.info("Council review {} ({}) recorded on application {} by {}",
                review.getReviewId(), status, request.applicationId(), ctx.actorId());
        return review;
    }

    @Transactional(readOnly = true)
    public List<ExternalCouncilReviewEntity> listCouncilReviews(UUID applicationId) {
        return councilReviewRepository.findByApplicationIdOrderByRecordedAtDesc(applicationId);
    }

    /**
     * Route gate consulted before committee decisions: the private route requires
     * an ENDORSED (or explicitly NOT_REQUIRED) council review when the catalogue
     * says so. Returns null when clear, or a human-readable blocker.
     */
    @Transactional(readOnly = true)
    public String councilReviewBlocker(FacilityApplicationEntity application) {
        String catalogueCode = application.getCatalogueCode();
        if (catalogueCode == null) {
            return null; // legacy applications: no catalogue policy attached
        }
        return applicationTypeRepository.findById(catalogueCode)
                .filter(RegulatoryApplicationTypeEntity::isCouncilReviewRequired)
                .map(type -> {
                    boolean satisfied = councilReviewRepository
                            .findByApplicationIdOrderByRecordedAtDesc(application.getApplicationId()).stream()
                            .anyMatch(r -> "ENDORSED".equals(r.getStatus()) || "NOT_REQUIRED".equals(r.getStatus()));
                    return satisfied ? null
                            : "Application type " + catalogueCode + " requires an external council review "
                              + "(ENDORSED or NOT_REQUIRED) before a committee decision";
                })
                .orElse(null);
    }

    // ---- helpers ---------------------------------------------------------

    private FacilityApplicationEntity requireApplication(UUID applicationId, TrustContext ctx) {
        FacilityApplicationEntity application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!application.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return application;
    }

    private ApplicationInformationRequestEntity requireRfi(UUID rfiId, TrustContext ctx) {
        ApplicationInformationRequestEntity rfi = rfiRepository.findById(rfiId)
                .orElseThrow(() -> new IllegalArgumentException("Information request not found: " + rfiId));
        if (!rfi.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return rfi;
    }

    private static void assertRole(TrustContext ctx, ActorTypeGuard.Duty duty) {
        ActorTypeGuard.require(ctx, duty);
    }

    private void publishEvent(TrustContext ctx, FacilityApplicationEntity application,
                              String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY");
        Long facilityId = application.getFacility() != null ? application.getFacility().getId() : null;
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
        event.setIdempotencyKey("tuso:appgov:" + eventType + ":" + UUID.randomUUID());
        outboxRepository.save(event);
    }
}
