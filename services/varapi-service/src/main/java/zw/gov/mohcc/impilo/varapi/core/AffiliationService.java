package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.integration.TusoClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing provider facility affiliations.
 * Handles the creation, approval, and management of provider-facility relationships.
 */
@Service
public class AffiliationService {

    private static final Logger log = LoggerFactory.getLogger(AffiliationService.class);

    private final ProviderAffiliationRepository affiliationRepository;
    private final ProviderRepository providerRepository;
    private final TusoClient tusoClient;
    private final EventOutboxRepository outboxRepository;

    public AffiliationService(
            ProviderAffiliationRepository affiliationRepository,
            ProviderRepository providerRepository,
            TusoClient tusoClient,
            EventOutboxRepository outboxRepository) {
        this.affiliationRepository = affiliationRepository;
        this.providerRepository = providerRepository;
        this.tusoClient = tusoClient;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a new provider affiliation proposal.
     */
    @Transactional
    public ProviderAffiliationEntity createAffiliation(
            Long providerId,
            Long facilityId,
            String affiliationType,
            String roleTitle,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating affiliation: providerId={}, facilityId={}, type={}, actor={}",
                providerId, facilityId, affiliationType, ctx.actorId());

        // Validate provider exists
        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        // Validate facility exists in TUSO
        if (!tusoClient.validateFacilityExists(facilityId)) {
            throw new IllegalArgumentException("Facility not found in registry: " + facilityId);
        }

        // If this is marked as primary, unset other primary affiliations
        if (Boolean.TRUE.equals(requestPrimary())) {
            List<ProviderAffiliationEntity> existingPrimary = affiliationRepository
                    .findByProviderIdAndPrimaryFlag(providerId, true);
            for (ProviderAffiliationEntity existing : existingPrimary) {
                existing.setPrimaryFlag(false);
                affiliationRepository.save(existing);
            }
        }

        ProviderAffiliationEntity affiliation = new ProviderAffiliationEntity();
        affiliation.setProvider(provider);
        affiliation.setTenantId(ctx.tenantId());
        affiliation.setFacilityId(facilityId);
        affiliation.setAffiliationType(affiliationType);
        affiliation.setRoleTitle(roleTitle);
        affiliation.setStartDate(startDate);
        affiliation.setEndDate(endDate);
        affiliation.setStatus("PROPOSED");
        affiliation.setApprovalState("PENDING");
        affiliation.setNotes(notes);
        affiliation.setVersion(1);
        affiliation.setCreatedBy(ctx.actorId());
        affiliation.setUpdatedBy(ctx.actorId());

        affiliation = affiliationRepository.save(affiliation);

        log.info("Affiliation created: id={}, providerId={}, facilityId={}",
                affiliation.getId(), providerId, facilityId);

        publishEvent("PROVIDER_AFFILIATION", affiliation.getId().toString(),
                "varapi.affiliation.created",
                String.format("{\"affiliationId\":%d,\"providerId\":%d,\"facilityId\":%d,\"type\":\"%s\"}",
                        affiliation.getId(), providerId, facilityId, affiliationType));

        return affiliation;
    }

    /**
     * Submit affiliation for approval.
     */
    @Transactional
    public ProviderAffiliationEntity submitForApproval(Long affiliationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Submitting affiliation for approval: id={}", affiliationId);

        ProviderAffiliationEntity affiliation = affiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new IllegalArgumentException("Affiliation not found: " + affiliationId));

        if (!"PROPOSED".equals(affiliation.getStatus())) {
            throw new IllegalStateException("Can only submit proposed affiliations");
        }

        affiliation.setApprovalState("PENDING");
        affiliation.setVersion(affiliation.getVersion() + 1);
        affiliation.setUpdatedBy(ctx.actorId());

        affiliation = affiliationRepository.save(affiliation);

        log.info("Affiliation submitted: id={}", affiliationId);
        return affiliation;
    }

    /**
     * Approve affiliation.
     */
    @Transactional
    public ProviderAffiliationEntity approveAffiliation(Long affiliationId, String authorityDecision, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Approving affiliation: id={}, actor={}", affiliationId, ctx.actorId());

        ProviderAffiliationEntity affiliation = affiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new IllegalArgumentException("Affiliation not found: " + affiliationId));

        if (!"PENDING".equals(affiliation.getApprovalState())) {
            throw new IllegalStateException("Can only approve pending affiliations");
        }

        // If this is primary, unset other primary affiliations
        if (Boolean.TRUE.equals(affiliation.getPrimaryFlag())) {
            List<ProviderAffiliationEntity> existingPrimary = affiliationRepository
                    .findByProviderIdAndPrimaryFlag(affiliation.getProvider().getId(), true);
            for (ProviderAffiliationEntity existing : existingPrimary) {
                if (!existing.getId().equals(affiliationId)) {
                    existing.setPrimaryFlag(false);
                    affiliationRepository.save(existing);
                }
            }
        }

        affiliation.setApprovalState("APPROVED");
        affiliation.setStatus("ACTIVE");
        affiliation.setLinkedAuthorityDecision(authorityDecision);
        affiliation.setNotes(notes != null ? notes : affiliation.getNotes());
        affiliation.setVersion(affiliation.getVersion() + 1);
        affiliation.setUpdatedBy(ctx.actorId());

        affiliation = affiliationRepository.save(affiliation);

        log.info("Affiliation approved: id={}", affiliationId);
        publishEvent("PROVIDER_AFFILIATION", affiliation.getId().toString(),
                "varapi.affiliation.approved",
                String.format("{\"affiliationId\":%d,\"providerId\":%d,\"facilityId\":%d}",
                        affiliationId, affiliation.getProvider().getId(), affiliation.getFacilityId()));

        return affiliation;
    }

    /**
     * Reject affiliation.
     */
    @Transactional
    public ProviderAffiliationEntity rejectAffiliation(Long affiliationId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Rejecting affiliation: id={}, actor={}", affiliationId, ctx.actorId());

        ProviderAffiliationEntity affiliation = affiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new IllegalArgumentException("Affiliation not found: " + affiliationId));

        affiliation.setApprovalState("REJECTED");
        affiliation.setStatus("TERMINATED");
        affiliation.setNotes(reason);
        affiliation.setVersion(affiliation.getVersion() + 1);
        affiliation.setUpdatedBy(ctx.actorId());

        affiliation = affiliationRepository.save(affiliation);

        log.info("Affiliation rejected: id={}", affiliationId);
        publishEvent("PROVIDER_AFFILIATION", affiliation.getId().toString(),
                "varapi.affiliation.rejected",
                String.format("{\"affiliationId\":%d}", affiliationId));

        return affiliation;
    }

    /**
     * Terminate an active affiliation.
     */
    @Transactional
    public ProviderAffiliationEntity terminateAffiliation(Long affiliationId, LocalDate endDate, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Terminating affiliation: id={}", affiliationId);

        ProviderAffiliationEntity affiliation = affiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new IllegalArgumentException("Affiliation not found: " + affiliationId));

        if (!"ACTIVE".equals(affiliation.getStatus())) {
            throw new IllegalStateException("Can only terminate active affiliations");
        }

        affiliation.setStatus("TERMINATED");
        affiliation.setEndDate(endDate != null ? endDate : LocalDate.now());
        affiliation.setNotes(reason);
        affiliation.setVersion(affiliation.getVersion() + 1);
        affiliation.setUpdatedBy(ctx.actorId());

        affiliation = affiliationRepository.save(affiliation);

        log.info("Affiliation terminated: id={}", affiliationId);
        publishEvent("PROVIDER_AFFILIATION", affiliation.getId().toString(),
                "varapi.affiliation.terminated",
                String.format("{\"affiliationId\":%d}", affiliationId));

        return affiliation;
    }

    /**
     * Get affiliation by ID.
     */
    @Transactional(readOnly = true)
    public ProviderAffiliationEntity getAffiliation(Long affiliationId) {
        return affiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new IllegalArgumentException("Affiliation not found: " + affiliationId));
    }

    /**
     * Get all affiliations for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderAffiliationEntity> getAffiliationsByProvider(Long providerId) {
        return affiliationRepository.findByProviderId(providerId);
    }

    /**
     * Get active affiliations for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderAffiliationEntity> getActiveAffiliationsByProvider(Long providerId) {
        return affiliationRepository.findByProviderIdAndStatus(providerId, "ACTIVE");
    }

    /**
     * Get active affiliations for a facility.
     */
    @Transactional(readOnly = true)
    public List<ProviderAffiliationEntity> getActiveAffiliationsByFacility(Long facilityId) {
        return affiliationRepository.findByFacilityIdAndStatus(facilityId, "ACTIVE");
    }

    /**
     * Get primary affiliation for a provider.
     */
    @Transactional(readOnly = true)
    public ProviderAffiliationEntity getPrimaryAffiliation(Long providerId) {
        List<ProviderAffiliationEntity> primary = affiliationRepository
                .findByProviderIdAndPrimaryFlag(providerId, true);
        return primary.isEmpty() ? null : primary.get(0);
    }

    /**
     * Check if provider has active affiliation to facility.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveAffiliation(Long providerId, Long facilityId) {
        List<ProviderAffiliationEntity> affiliations = affiliationRepository
                .findByProviderIdAndStatus(providerId, "ACTIVE");
        return affiliations.stream().anyMatch(a -> a.getFacilityId() != null && a.getFacilityId().equals(facilityId));
    }

    private boolean requestPrimary() {
        // This would be determined by request parameter in controller
        return false;
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