package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
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
        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        // Validate facility exists in TUSO
        if (!tusoClient.validateFacilityExists(facilityId)) {
            throw new IllegalArgumentException("Facility not found in registry: " + facilityId);
        }

        // Validate provider eligibility
        validateProviderEligibility(provider);

        // Check if facility already has an active PIC
        Optional<PractitionerInChargeAssignmentEntity> existingPic = picRepository
                .findByFacilityIdAndStatusAndEndDateIsNull(facilityId, "ACTIVE");
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

        publishEvent("PIC_ASSIGNMENT", assignment.getId().toString(),
                "varapi.pic.assignment.created",
                String.format("{\"assignmentId\":%d,\"providerId\":%d,\"facilityId\":%d}",
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

        PractitionerInChargeAssignmentEntity assignment = picRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

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
        publishEvent("PIC_ASSIGNMENT", assignment.getId().toString(),
                "varapi.pic.assignment.approved",
                String.format("{\"assignmentId\":%d,\"providerId\":%d,\"facilityId\":%d}",
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

        PractitionerInChargeAssignmentEntity assignment = picRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

        assignment.setApprovalState("REJECTED");
        assignment.setStatus("TERMINATED");
        assignment.setNotes(reason);
        assignment.setVersion(assignment.getVersion() + 1);
        assignment.setUpdatedBy(ctx.actorId());

        assignment = picRepository.save(assignment);

        log.info("PIC assignment rejected: id={}", assignmentId);
        publishEvent("PIC_ASSIGNMENT", assignment.getId().toString(),
                "varapi.pic.assignment.rejected",
                String.format("{\"assignmentId\":%d}", assignmentId));

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

        PractitionerInChargeAssignmentEntity assignment = picRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

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
        publishEvent("PIC_ASSIGNMENT", assignment.getId().toString(),
                "varapi.pic.assignment.terminated",
                String.format("{\"assignmentId\":%d}", assignmentId));

        return assignment;
    }

    /**
     * Check if provider is eligible to serve as PIC.
     */
    @Transactional(readOnly = true)
    public boolean isProviderEligibleForPic(Long providerId) {
        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        return validateProviderEligibility(provider) == null;
    }

    /**
     * Get current PIC for a facility.
     */
    @Transactional(readOnly = true)
    public Optional<PractitionerInChargeAssignmentEntity> getCurrentPicForFacility(Long facilityId) {
        return picRepository.findByFacilityIdAndStatusAndEndDateIsNull(facilityId, "ACTIVE");
    }

    /**
     * Get all PIC assignments for a provider.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getAssignmentsByProvider(Long providerId) {
        return picRepository.findByProviderId(providerId);
    }

    /**
     * Get all PIC assignments for a facility.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getAssignmentsByFacility(Long facilityId) {
        return picRepository.findByFacilityId(facilityId);
    }

    /**
     * Get all active PIC assignments for a facility.
     */
    @Transactional(readOnly = true)
    public List<PractitionerInChargeAssignmentEntity> getActiveAssignmentsByFacility(Long facilityId) {
        return picRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE");
    }

    /**
     * Get assignment by ID.
     */
    @Transactional(readOnly = true)
    public PractitionerInChargeAssignmentEntity getAssignment(Long assignmentId) {
        return picRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
    }

    /**
     * Validate provider eligibility for PIC role.
     * Returns null if eligible, error message if not.
     */
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

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}