package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;
import zw.gov.mohcc.impilo.varapi.integration.TusoClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PractitionerInChargeAssignmentEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.PractitionerInChargeAssignmentRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing Practitioner-in-Charge (PIC) assignments.
 * Validates provider eligibility and manages the assignment lifecycle.
 */
@Service
public class PractitionerInChargeService {

    private static final Logger log = LoggerFactory.getLogger(PractitionerInChargeService.class);

    private final PractitionerInChargeAssignmentRepository picRepository;
    private final ProviderRepository providerRepository;
    private final LicenseRepository licenseRepository;
    private final TusoClient tusoClient;
    private final EventOutboxRepository outboxRepository;
    private final EventTopicRegistry topics = new EventTopicRegistry("varapi");

    public PractitionerInChargeService(
            PractitionerInChargeAssignmentRepository picRepository,
            ProviderRepository providerRepository,
            LicenseRepository licenseRepository,
            TusoClient tusoClient,
            EventOutboxRepository outboxRepository) {
        this.picRepository = picRepository;
        this.providerRepository = providerRepository;
        this.licenseRepository = licenseRepository;
        this.tusoClient = tusoClient;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a new PIC assignment proposal.
     */
    @Transactional
    public PractitionerInChargeAssignmentEntity createAssignment(
            Long providerId,
            Long facilityId,
            String assignmentType,
            LocalDate startDate,
            LocalDate endDate,
            String sourceCouncilReference,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating PIC assignment: providerId={}, facilityId={}, type={}, actor={}",
                providerId, facilityId, assignmentType, ctx.actorId());

        // Validate provider exists
        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());

        // Validate facility exists in TUSO
        if (!tusoClient.validateFacilityExists(facilityId)) {
            throw new IllegalArgumentException("Facility not found in registry: " + facilityId);
        }

        // Validate provider eligibility
        validateProviderEligibility(provider);

        // Check if facility already has an active PIC
        Optional<PractitionerInChargeAssignmentEntity> existingPic = picRepository
                .findByTenantIdAndFacilityIdAndStatusAndEndDateIsNull(ctx.tenantId(), facilityId, "ACTIVE");
        if (existingPic.isPresent()) {
            log.warn("Facility {} already has an active PIC: {}", facilityId, existingPic.get().getId());
        }

        PractitionerInChargeAssignmentEntity assignment = new PractitionerInChargeAssignmentEntity();
        assignment.setProvider(provider);
        assignment.setTenantId(ctx.tenantId());
        assignment.setFacilityId(facilityId);
        assignment.setAssignmentType(assignmentType != null ? assignmentType : "PRACTITIONER_IN_CHARGE");
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setStatus("ACTIVE");
        assignment.setApprovalState("PENDING");
        assignment.setSourceCouncilReference(sourceCouncilReference);
        assignment.setNotes(notes);
        assignment.setVersion(1);
        assignment.setCreatedBy(ctx.actorId());
        assignment.setUpdatedBy(ctx.actorId());

        assignment = picRepository.save(assignment);

        log.info("PIC assignment created: id={}, providerId={}, facilityId={}",
                assignment.getId(), providerId, facilityId);

        publishEvent(ctx, "PIC_ASSIGNMENT", assignment.getId().toString(),
                topics.eventType("pic_assignment", "created"),
                String.format("{\"assignmentId\":%d,\"providerId\":%d,\"facilityId\":%d,\"approvalState\":\"PENDING\"}",
                        assignment.getId(), providerId, facilityId));

        return assignment;
    }

    /**
     * Approve PIC assignment.
     */
    @Transactional
    public PractitionerInChargeAssignmentEntity approveAssignment(
            Long assignmentId,
            String decisionReference,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Approving PIC assignment: id={}, actor={}", assignmentId, ctx.actorId());

        PractitionerInChargeAssignmentEntity assignment = requireAssignment(assignmentId, ctx.tenantId());

        if (!"PENDING".equals(assignment.getApprovalState())) {
            throw new IllegalStateException("Can only approve pending assignments");
        }

        assignment.setApprovalState("APPROVED");
        assignment.setDecisionReference(decisionReference);
        assignment.setNotes(notes != null ? notes : assignment.getNotes());
        assignment.setVersion(assignment.getVersion() + 1);
        assignment.setUpdatedBy(ctx.actorId());

        assignment = picRepository.save(assignment);

        log.info("PIC assignment approved: id={}", assignmentId);
        publishEvent(ctx, "PIC_ASSIGNMENT", assignment.getId().toString(),
                topics.eventType("pic_assignment", "approved"),
                String.format("{\"assignmentId\":%d,\"providerId\":%d,\"facilityId\":%d,\"approvalState\":\"APPROVED\"}",
                        assignmentId, assignment.getProvider().getId(), assignment.getFacilityId()));

        return assignment;
    }

    /**
     * Reject PIC assignment.
     */
    @Transactional
    public PractitionerInChargeAssignmentEntity rejectAssignment(Long assignmentId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Rejecting PIC assignment: id={}, actor={}", assignmentId, ctx.actorId());

        PractitionerInChargeAssignmentEntity assignment = requireAssignment(assignmentId, ctx.tenantId());

        assignment.setApprovalState("REJECTED");
        assignment.setStatus("TERMINATED");
        assignment.setNotes(reason);
        assignment.setVersion(assignment.getVersion() + 1);
        assignment.setUpdatedBy(ctx.actorId());

        assignment = picRepository.save(assignment);

        log.info("PIC assignment rejected: id={}", assignmentId);
        publishEvent(ctx, "PIC_ASSIGNMENT", assignment.getId().toString(),
                topics.eventType("pic_assignment", "rejected"),
                String.format("{\"assignmentId\":%d,\"approvalState\":\"REJECTED\",\"reason\":%s}",
                        assignmentId, reason != null ? "\"" + reason.replace("\"", "\\\"") + "\"" : "null"));

        return assignment;
    }

    /**
     * Terminate an active PIC assignment.
     */
    @Transactional
    public PractitionerInChargeAssignmentEntity terminateAssignment(
            Long assignmentId,
            LocalDate endDate,
            String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Terminating PIC assignment: id={}", assignmentId);

        PractitionerInChargeAssignmentEntity assignment = requireAssignment(assignmentId, ctx.tenantId());

        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new IllegalStateException("Can only terminate active assignments");
        }

        assignment.setStatus("TERMINATED");
        assignment.setEndDate(endDate != null ? endDate : LocalDate.now());
        assignment.setNotes(reason);
        assignment.setVersion(assignment.getVersion() + 1);
        assignment.setUpdatedBy(ctx.actorId());

        assignment = picRepository.save(assignment);

        log.info("PIC assignment terminated: id={}", assignmentId);
        publishEvent(ctx, "PIC_ASSIGNMENT", assignment.getId().toString(),
                topics.eventType("pic_assignment", "terminated"),
                String.format("{\"assignmentId\":%d,\"status\":\"TERMINATED\"}", assignmentId));

        return assignment;
    }

    /**
     * Check if provider is eligible to serve as PIC.
     */
    @Transactional(readOnly = true)
    public boolean isProviderEligibleForPic(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());

        return validateProviderEligibility(provider) == null;
    }

    /**
     * Get current PIC for a facility.
     */
    @Transactional(readOnly = true)
    public Optional<PractitionerInChargeAssignmentEntity> getCurrentPicForFacility(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return picRepository.findByTenantIdAndFacilityIdAndStatusAndEndDateIsNull(ctx.tenantId(), facilityId, "ACTIVE");
    }

    /**
     * Get all PIC assignments for a provider.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getAssignmentsByProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return picRepository.findByTenantIdAndProviderId(ctx.tenantId(), providerId);
    }

    /**
     * Get all PIC assignments for a facility.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getAssignmentsByFacility(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return picRepository.findByTenantIdAndFacilityId(ctx.tenantId(), facilityId);
    }

    /**
     * Get all active PIC assignments for a facility.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getActiveAssignmentsByFacility(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return picRepository.findByTenantIdAndFacilityIdAndStatus(ctx.tenantId(), facilityId, "ACTIVE");
    }

    /**
     * Get assignment by ID.
     */
    @Transactional(readOnly = true)
    public PractitionerInChargeAssignmentEntity getAssignment(Long assignmentId) {
        TrustContext ctx = TrustContextHolder.require();
        return requireAssignment(assignmentId, ctx.tenantId());
    }

    /**
     * Validate provider eligibility for PIC role.
     * Returns null if eligible, error message if not.
     */
    /**
     * Revoke a PIC assignment.
     */
    @Transactional
    public PractitionerInChargeAssignmentEntity revokeAssignment(Long assignmentId, LocalDate revocationDate, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking PIC assignment: id={}", assignmentId);

        PractitionerInChargeAssignmentEntity assignment = requireAssignment(assignmentId, ctx.tenantId());

        assignment.setStatus("REVOKED");
        assignment.setEndDate(revocationDate != null ? revocationDate : LocalDate.now());
        assignment.setNotes("Revoked: " + reason);
        assignment.setVersion(assignment.getVersion() + 1);
        assignment.setUpdatedBy(ctx.actorId());

        assignment = picRepository.save(assignment);
        log.info("PIC assignment revoked: id={}", assignmentId);
        return assignment;
    }

    /**
     * Transfer PIC assignment to a new facility.
     */
    @Transactional
    public PractitionerInChargeAssignmentEntity transferAssignment(Long assignmentId, Long newFacilityId, LocalDate transferDate, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Transferring PIC assignment: id={} to facilityId={}", assignmentId, newFacilityId);

        PractitionerInChargeAssignmentEntity existing = requireAssignment(assignmentId, ctx.tenantId());

        // Terminate existing
        existing.setStatus("TRANSFERRED");
        existing.setEndDate(transferDate != null ? transferDate : LocalDate.now());
        existing.setNotes("Transferred to facility " + newFacilityId + ": " + reason);
        existing.setVersion(existing.getVersion() + 1);
        picRepository.save(existing);

        // Create new assignment
        PractitionerInChargeAssignmentEntity newAssignment = new PractitionerInChargeAssignmentEntity();
        newAssignment.setProvider(existing.getProvider());
        newAssignment.setTenantId(ctx.tenantId());
        newAssignment.setFacilityId(newFacilityId);
        newAssignment.setAssignmentType(existing.getAssignmentType());
        newAssignment.setStartDate(transferDate != null ? transferDate : LocalDate.now());
        newAssignment.setStatus("ACTIVE");
        newAssignment.setApprovalState("PENDING");
        newAssignment.setNotes("Transferred from assignment " + assignmentId + ": " + reason);
        newAssignment.setVersion(1);
        newAssignment.setCreatedBy(ctx.actorId());
        newAssignment.setUpdatedBy(ctx.actorId());

        newAssignment = picRepository.save(newAssignment);
        log.info("PIC assignment transferred: newId={} from assignmentId={}", newAssignment.getId(), assignmentId);
        return newAssignment;
    }

    /**
     * Get active PIC assignment by provider.
     */
    @Transactional(readOnly = true)
    public PractitionerInChargeAssignmentEntity getActiveAssignmentByProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        List<PractitionerInChargeAssignmentEntity> active = picRepository
                .findByTenantIdAndProviderIdAndStatusOrderByStartDateDescIdDesc(ctx.tenantId(), providerId, "ACTIVE");
        return active.isEmpty() ? null : active.get(0);
    }

    /**
     * Get active PIC assignment by facility.
     */
    @Transactional(readOnly = true)
    public PractitionerInChargeAssignmentEntity getActiveAssignmentByFacility(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return picRepository.findByTenantIdAndFacilityIdAndStatusAndEndDateIsNull(ctx.tenantId(), facilityId, "ACTIVE")
                .orElse(null);
    }

    /**
     * Get PIC assignment history by facility.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getAssignmentHistoryByFacility(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return picRepository.findByTenantIdAndFacilityIdOrderByStartDateDescIdDesc(ctx.tenantId(), facilityId);
    }

    /**
     * Get PIC assignment history by provider.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getAssignmentHistoryByProvider(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireProvider(providerId, ctx.tenantId());
        return picRepository.findByTenantIdAndProviderIdOrderByStartDateDescIdDesc(ctx.tenantId(), providerId);
    }

    /**
     * Get eligible providers for a facility.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEligibleProvidersForFacility(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<ProviderEntity> activeProviders = providerRepository.findByTenantIdAndStatus(ctx.tenantId(), "ACTIVE");
        return activeProviders.stream()
                .filter(p -> validateProviderEligibility(p) == null)
                .map(p -> Map.<String, Object>of(
                        "providerId", p.getId(),
                        "providerPublicId", p.getProviderPublicId(),
                        "status", p.getStatus()))
                .toList();
    }

    private ProviderEntity requireProvider(Long providerId, java.util.UUID tenantId) {
        return providerRepository.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
    }

    private PractitionerInChargeAssignmentEntity requireAssignment(Long assignmentId, java.util.UUID tenantId) {
        return picRepository.findByIdAndTenantId(assignmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
    }

    private String validateProviderEligibility(ProviderEntity provider) {
        // Check provider status
        if (!"ACTIVE".equals(provider.getStatus())) {
            return "Provider is not active";
        }

        // Check provider has valid license
        List<LicenseEntity> activeLicenses = licenseRepository.findByProviderId(provider.getId())
                .stream()
                .filter(l -> "ACTIVE".equals(l.getStatus()))
                .filter(l -> l.getValidTo() == null || l.getValidTo().isAfter(LocalDate.now()))
                .toList();

        if (activeLicenses.isEmpty()) {
            return "Provider has no valid license";
        }

        // Check for any suspensions or restrictions
        List<LicenseEntity> suspendedLicenses = licenseRepository.findByProviderId(provider.getId())
                .stream()
                .filter(l -> "SUSPENDED".equals(l.getStatus()))
                .toList();

        if (!suspendedLicenses.isEmpty()) {
            return "Provider has suspended license(s)";
        }

        return null;
    }

    private void publishEvent(TrustContext ctx, String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setSubjectType(aggregateType);
        event.setSubjectId(aggregateId);
        event.setPartitionKey(aggregateId);
        outboxRepository.save(event);
    }
}
