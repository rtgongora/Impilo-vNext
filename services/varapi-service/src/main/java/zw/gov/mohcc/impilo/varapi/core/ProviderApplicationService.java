package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderStatusHistoryEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderApplicationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderStatusHistoryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing provider lifecycle applications.
 * Handles new registrations, renewals, specialty recognitions, material changes,
 * and restoration applications with proper workflow state transitions.
 */
@Service
public class ProviderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProviderApplicationService.class);
    private static final List<String> OPEN_WORKFLOW_STATES = List.of(
            "SUBMITTED",
            "UNDER_ADMIN_REVIEW",
            "AWAITING_DOCUMENTS",
            "AWAITING_VERIFICATION",
            "AWAITING_PAYMENT",
            "READY_FOR_REVIEW",
            "PENDING_COMMITTEE_REVIEW");

    private final ProviderApplicationRepository applicationRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStatusHistoryRepository statusHistoryRepository;
    private final EventOutboxRepository outboxRepository;
    private final CouncilRepository councilRepository;

    public ProviderApplicationService(
            ProviderApplicationRepository applicationRepository,
            ProviderRepository providerRepository,
            ProviderStatusHistoryRepository statusHistoryRepository,
            EventOutboxRepository outboxRepository,
            CouncilRepository councilRepository) {
        this.applicationRepository = applicationRepository;
        this.providerRepository = providerRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxRepository = outboxRepository;
        this.councilRepository = councilRepository;
    }

    /**
     * Create a new provider application.
     */
    @Transactional
    public ProviderApplicationEntity createApplication(
            String applicationType,
            Long providerId,
            Long councilId,
            String authorityRoute,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating application: type={}, providerId={}, councilId={}, actor={}",
                applicationType, providerId, councilId, ctx.actorId());

        ProviderApplicationEntity application = new ProviderApplicationEntity();
        application.setTenantId(ctx.tenantId());
        application.setApplicationType(applicationType);
        application.setWorkflowState("DRAFT");
        application.setAuthorityRoute(authorityRoute);
        application.setNotes(notes);
        application.setVersion(1);
        application.setCreatedBy(ctx.actorId());
        application.setUpdatedBy(ctx.actorId());

        if (providerId != null) {
            application.setProvider(requireProvider(providerId, ctx.tenantId()));
        }
        if (councilId != null) {
            CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, ctx.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));
            application.setCouncil(council);
        }

        application = applicationRepository.save(application);

        log.info("Application created: id={}, type={}", application.getId(), applicationType);
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "varapi.application.created",
                String.format("{\"applicationId\":%d,\"type\":\"%s\",\"workflowState\":\"DRAFT\"}",
                        application.getId(), applicationType));

        return application;
    }

    /**
     * Submit an application for review.
     * Transitions from DRAFT to SUBMITTED.
     */
    @Transactional
    public ProviderApplicationEntity submitApplication(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Submitting application: id={}, actor={}", applicationId, ctx.actorId());

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        validateTransition(application.getWorkflowState(), "SUBMITTED");
        validateSubmissionRequirements(application);

        application.setWorkflowState("SUBMITTED");
        application.setSubmittedAt(Instant.now());
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Application submitted: id={}", applicationId);
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "varapi.application.submitted",
                String.format("{\"applicationId\":%d}", applicationId));

        return application;
    }

    /**
     * Council intake: move from submitted to under admin review.
     */
    @Transactional
    public ProviderApplicationEntity advanceToUnderAdminReview(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());
        validateTransition(application.getWorkflowState(), "UNDER_ADMIN_REVIEW");
        application.setWorkflowState("UNDER_ADMIN_REVIEW");
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());
        return applicationRepository.save(application);
    }

    /**
     * Require fee payment before substantive review (MusheX is system of record for money).
     */
    @Transactional
    public ProviderApplicationEntity advanceToAwaitingPayment(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());
        validateTransition(application.getWorkflowState(), "AWAITING_PAYMENT");
        application.setWorkflowState("AWAITING_PAYMENT");
        application.setFeeState("UNPAID");
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());
        application = applicationRepository.save(application);
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "provider_council.application.awaiting_payment",
                String.format("{\"applicationId\":%d}", applicationId));
        return application;
    }

    /**
     * Called when MusheX reports obligation settled / fee paid — advances workflow toward review.
     */
    @Transactional
    public ProviderApplicationEntity advanceAfterFeePayment(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());
        if (!"AWAITING_PAYMENT".equals(application.getWorkflowState())) {
            throw new IllegalStateException("Application is not awaiting payment: " + application.getWorkflowState());
        }
        validateTransition(application.getWorkflowState(), "READY_FOR_REVIEW");
        application.setWorkflowState("READY_FOR_REVIEW");
        application.setFeeState("PAID");
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());
        application = applicationRepository.save(application);
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "provider_council.payment.settled",
                String.format("{\"applicationId\":%d}", applicationId));
        return application;
    }

    /**
     * Council queue filtered by council and workflow states.
     */
    @Transactional(readOnly = true)
    public List<ProviderApplicationEntity> getApplicationsForCouncil(Long councilId, List<String> workflowStates) {
        TrustContext ctx = TrustContextHolder.require();
        if (workflowStates == null || workflowStates.isEmpty()) {
            return applicationRepository.findByTenantIdAndWorkflowStateIn(ctx.tenantId(), OPEN_WORKFLOW_STATES)
                    .stream()
                    .filter(a -> a.getCouncil() != null && councilId.equals(a.getCouncil().getId()))
                    .toList();
        }
        return applicationRepository.findByTenantIdAndCouncil_IdAndWorkflowStateIn(
                ctx.tenantId(), councilId, workflowStates);
    }

    /**
     * Advance application to verification stage.
     */
    @Transactional
    public ProviderApplicationEntity advanceToVerification(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Advancing to verification: id={}", applicationId);

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        if (!"UNDER_ADMIN_REVIEW".equals(application.getWorkflowState())) {
            throw new IllegalStateException("Can only advance applications that are under admin review");
        }
        validateTransition(application.getWorkflowState(), "AWAITING_VERIFICATION");

        application.setWorkflowState("AWAITING_VERIFICATION");
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Application advanced to verification: id={}", applicationId);
        return application;
    }

    /**
     * Mark application as awaiting documents.
     */
    @Transactional
    public ProviderApplicationEntity requestDocuments(Long applicationId, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Requesting documents: id={}", applicationId);

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        validateTransition(application.getWorkflowState(), "AWAITING_DOCUMENTS");

        application.setWorkflowState("AWAITING_DOCUMENTS");
        application.setNotes(notes != null ? notes : application.getNotes());
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Documents requested: id={}", applicationId);
        return application;
    }

    /**
     * Record verification completion and advance to review.
     */
    @Transactional
    public ProviderApplicationEntity completeVerification(Long applicationId, String verificationState) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Completing verification: id={}, state={}", applicationId, verificationState);

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        application.setVerificationState(verificationState);

        if ("VERIFIED".equals(verificationState)) {
            application.setWorkflowState("READY_FOR_REVIEW");
        } else if ("FAILED".equals(verificationState)) {
            application.setWorkflowState("AWAITING_DOCUMENTS");
        }

        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Verification completed: id={}, newState={}", applicationId, application.getWorkflowState());
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "varapi.application.verification.completed",
                String.format("{\"applicationId\":%d,\"verificationState\":\"%s\"}",
                        applicationId, verificationState));

        return application;
    }

    /**
     * Advance to committee review.
     */
    @Transactional
    public ProviderApplicationEntity advanceToCommitteeReview(Long applicationId, String committeeState) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Advancing to committee: id={}", applicationId);

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        validateTransition(application.getWorkflowState(), "PENDING_COMMITTEE_REVIEW");

        application.setWorkflowState("PENDING_COMMITTEE_REVIEW");
        application.setCommitteeState(committeeState);
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Advanced to committee: id={}, committee={}", applicationId, committeeState);
        return application;
    }

    /**
     * Record committee decision.
     */
    @Transactional
    public ProviderApplicationEntity recordDecision(
            Long applicationId,
            String decision,
            LocalDate decisionDate,
            String authorityRoute) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Recording decision: id={}, decision={}, actor={}", applicationId, decision, ctx.actorId());

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        String targetState = validateAndGetDecisionState(decision);
        validateTransition(application.getWorkflowState(), targetState);

        application.setFinalDecision(decision);
        application.setDecisionDate(decisionDate != null ? decisionDate : LocalDate.now());
        application.setAuthorityRoute(authorityRoute != null ? authorityRoute : application.getAuthorityRoute());
        application.setWorkflowState(targetState);
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Decision recorded: id={}, decision={}", applicationId, decision);
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "varapi.application.decided",
                String.format("{\"applicationId\":%d,\"decision\":\"%s\"}",
                        applicationId, decision));

        return application;
    }

    /**
     * Close an application without decision.
     */
    @Transactional
    public ProviderApplicationEntity closeApplication(Long applicationId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Closing application: id={}", applicationId);

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        application.setWorkflowState("CLOSED_OUT");
        application.setNotes(reason != null ? reason : application.getNotes());
        application.setClosedAt(Instant.now());
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(ctx.actorId());

        application = applicationRepository.save(application);

        log.info("Application closed: id={}", applicationId);
        publishEvent("PROVIDER_APPLICATION", application.getId().toString(),
                "varapi.application.closed",
                String.format("{\"applicationId\":%d}", applicationId));

        return application;
    }

    /**
     * Approve application and activate provider.
     */
    @Transactional
    public ProviderApplicationEntity approveAndActivate(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Approving and activating: id={}", applicationId);

        ProviderApplicationEntity application = requireApplication(applicationId, ctx.tenantId());

        if (application.getProvider() != null) {
            ProviderEntity provider = application.getProvider();

            // Record status history
            ProviderStatusHistoryEntity history = new ProviderStatusHistoryEntity();
            history.setProvider(provider);
            history.setTenantId(ctx.tenantId());
            history.setPreviousStatus(provider.getStatus());
            history.setNewStatus("LICENCED_ACTIVE");
            history.setReason("Application approved: " + applicationId);
            history.setChangedBy(ctx.actorId());
            history.setAuthorityContext(application.getAuthorityRoute());
            history.setChangedAt(Instant.now());
            statusHistoryRepository.save(history);

            // Update provider
            provider.setStatus("ACTIVE");
            provider.setLifecycleStatus("LICENCED_ACTIVE");
            provider.setLicenceStatus("ACTIVE");
            provider.setActiveFlag(true);
            provider.setEffectiveFrom(LocalDate.now());
            provider.setEffectiveTo(null);
            provider.setVersion(provider.getVersion() + 1);
            provider.setUpdatedBy(ctx.actorId());
            providerRepository.save(provider);

            log.info("Provider activated: providerPublicId={}", provider.getProviderPublicId());
            publishEvent("PROVIDER", provider.getProviderPublicId(),
                    "varapi.provider.activated",
                    String.format("{\"providerPublicId\":\"%s\",\"applicationId\":%d}",
                            provider.getProviderPublicId(), applicationId));
        }

        return recordDecision(applicationId, "DECIDED_APPROVED", LocalDate.now(), application.getAuthorityRoute());
    }

    /**
     * Get application by ID.
     */
    @Transactional(readOnly = true)
    public ProviderApplicationEntity getApplication(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return requireApplication(applicationId, ctx.tenantId());
    }

    /**
     * Get all applications for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderApplicationEntity> getApplicationsByProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return applicationRepository.findByTenantIdAndProviderId(ctx.tenantId(), providerId);
    }

    /**
     * Get open applications for a tenant.
     */
    @Transactional(readOnly = true)
    public List<ProviderApplicationEntity> getOpenApplications(String workflowState) {
        TrustContext ctx = TrustContextHolder.require();
        if (workflowState == null || workflowState.isBlank()) {
            return applicationRepository.findByTenantIdAndWorkflowStateIn(ctx.tenantId(), OPEN_WORKFLOW_STATES);
        }
        return applicationRepository.findByTenantIdAndWorkflowState(ctx.tenantId(), workflowState);
    }

    // Private helpers

    private void validateTransition(String current, String target) {
        boolean valid = switch (current) {
            case "DRAFT" -> "SUBMITTED".equals(target);
            case "SUBMITTED" -> "UNDER_ADMIN_REVIEW".equals(target) || "AWAITING_DOCUMENTS".equals(target)
                    || "AWAITING_VERIFICATION".equals(target) || "AWAITING_PAYMENT".equals(target);
            case "UNDER_ADMIN_REVIEW" -> "AWAITING_DOCUMENTS".equals(target) || "AWAITING_VERIFICATION".equals(target)
                    || "AWAITING_PAYMENT".equals(target);
            case "AWAITING_DOCUMENTS" -> "SUBMITTED".equals(target);
            case "AWAITING_VERIFICATION" -> "READY_FOR_REVIEW".equals(target) || "AWAITING_DOCUMENTS".equals(target)
                    || "AWAITING_PAYMENT".equals(target);
            case "AWAITING_PAYMENT" -> "READY_FOR_REVIEW".equals(target) || "CLOSED_OUT".equals(target);
            case "READY_FOR_REVIEW" -> "PENDING_COMMITTEE_REVIEW".equals(target) || "AWAITING_PAYMENT".equals(target);
            case "PENDING_COMMITTEE_REVIEW" -> "DECIDED_APPROVED".equals(target) || "DECIDED_REJECTED".equals(target) || "DECIDED_DEFERRED".equals(target);
            case "DECIDED_REJECTED", "DECIDED_DEFERRED" -> "CLOSED_OUT".equals(target);
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format("Invalid workflow transition: %s -> %s", current, target));
        }
    }

    private void validateSubmissionRequirements(ProviderApplicationEntity application) {
        // Validate required fields based on application type
        if (application.getProvider() == null && !"NEW_REGISTRATION".equals(application.getApplicationType())) {
            throw new IllegalStateException("Provider is required for this application type");
        }
    }

    private ProviderEntity requireProvider(Long providerId, UUID tenantId) {
        return providerRepository.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
    }

    private ProviderApplicationEntity requireApplication(Long applicationId, UUID tenantId) {
        return applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }

    private String validateAndGetDecisionState(String decision) {
        if ("APPROVED".equals(decision) || "DECIDED_APPROVED".equals(decision)) {
            return "DECIDED_APPROVED";
        } else if ("REJECTED".equals(decision) || "DECIDED_REJECTED".equals(decision)) {
            return "DECIDED_REJECTED";
        } else if ("DEFERRED".equals(decision) || "DECIDED_DEFERRED".equals(decision)) {
            return "DECIDED_DEFERRED";
        }
        throw new IllegalArgumentException("Invalid decision: " + decision);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
