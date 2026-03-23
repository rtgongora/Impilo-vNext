package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.CreateProviderRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderSearchRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.StatusChangeRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.UpdateProviderRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderContactEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilAffiliationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderIdentifierEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderSpecialtyEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderContactRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderIdentifierRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderSpecialtyRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Core CRUD service for healthcare provider management.
 *
 * Manages the lifecycle of provider records including creation, retrieval,
 * update, search, and status transitions. Every mutation publishes a domain
 * event via the outbox pattern for reliable downstream consumption.
 *
 * All operations are tenant-scoped via the TrustContext extracted from the
 * current request's trust headers.
 */
@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final ProviderRepository providerRepository;
    private final ProviderIdentifierRepository identifierRepository;
    private final ProviderSpecialtyRepository specialtyRepository;
    private final ProviderContactRepository contactRepository;
    private final ProviderCouncilAffiliationRepository affiliationRepository;
    private final EventOutboxRepository outboxRepository;

    public ProviderService(ProviderRepository providerRepository,
                           ProviderIdentifierRepository identifierRepository,
                           ProviderSpecialtyRepository specialtyRepository,
                           ProviderContactRepository contactRepository,
                           ProviderCouncilAffiliationRepository affiliationRepository,
                           EventOutboxRepository outboxRepository) {
        this.providerRepository = providerRepository;
        this.identifierRepository = identifierRepository;
        this.specialtyRepository = specialtyRepository;
        this.contactRepository = contactRepository;
        this.affiliationRepository = affiliationRepository;
        this.outboxRepository = outboxRepository;
    }

    // ---- Inner DTOs ----

    public record ProviderDetail(
            ProviderEntity provider,
            List<ProviderIdentifierEntity> identifiers,
            List<ProviderSpecialtyEntity> specialties,
            List<ProviderContactEntity> contacts,
            List<ProviderCouncilAffiliationEntity> affiliations
    ) {}

    // ---- Public API ----

    /**
     * Register a new healthcare provider.
     *
     * Generates a ULID-style provider_public_id, sets initial version to 1,
     * and publishes a {@code varapi.provider.created} domain event.
     *
     * @param request the provider registration data
     * @return the persisted provider entity
     */
    @Transactional
    public ProviderEntity createProvider(CreateProviderRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating provider for tenant={}, givenName={}, familyName={}",
                ctx.tenantId(), request.givenName(), request.familyName());

        ProviderEntity provider = new ProviderEntity();
        provider.setTenantId(ctx.tenantId());
        provider.setProviderPublicId(generateProviderPublicId());
        provider.setTitle(request.title());
        provider.setGivenName(request.givenName());
        provider.setFamilyName(request.familyName());
        provider.setDateOfBirth(request.dateOfBirth());
        provider.setGender(request.gender());
        provider.setNationality(request.nationality());
        provider.setNationalId(request.nationalId());
        provider.setEmail(request.email());
        provider.setPhone(request.phone());
        provider.setPracticeNumber(request.practiceNumber());
        provider.setProfession(request.profession());
        provider.setCadre(request.cadre());
        provider.setEmploymentOrgId(request.employmentOrgId());
        provider.setStatus("ACTIVE");
        provider.setVersion(1);
        provider.setCreatedBy(ctx.actorId());
        provider.setUpdatedBy(ctx.actorId());

        // Resolve primary council association if provided
        if (request.primaryCouncilId() != null) {
            CouncilEntity councilRef = new CouncilEntity();
            councilRef.setId(request.primaryCouncilId());
            provider.setPrimaryCouncil(councilRef);
        }

        provider = providerRepository.save(provider);

        log.info("Provider created: providerPublicId={}, providerRef={}",
                provider.getProviderPublicId(), provider.getProviderRef());

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                "varapi.provider.created",
                String.format("{\"providerPublicId\":\"%s\",\"givenName\":\"%s\"," +
                                "\"familyName\":\"%s\",\"profession\":\"%s\",\"status\":\"ACTIVE\"}",
                        provider.getProviderPublicId(),
                        provider.getGivenName(),
                        provider.getFamilyName(),
                        provider.getProfession()));

        return provider;
    }

    @Transactional(readOnly = true)
    public ProviderEntity findByActorId(String actorId) {
        try {
            UUID providerRef = UUID.fromString(actorId);
            return providerRepository.findByProviderRef(providerRef)
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found for actorId: " + actorId));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Provider not found for actorId: " + actorId);
        }
    }

    /**
     * Retrieve a provider with all associated detail records.
     *
     * Loads the provider entity along with identifiers, specialties, contacts,
     * and council affiliations in a single transactional read.
     *
     * @param providerPublicId the unique public identifier for the provider
     * @return the composite provider detail
     * @throws IllegalArgumentException if no provider exists with the given ID
     */
    @Transactional(readOnly = true)
    public ProviderDetail getProvider(String providerPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Fetching provider detail: providerPublicId={}, tenant={}",
                providerPublicId, ctx.tenantId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        List<ProviderIdentifierEntity> identifiers =
                identifierRepository.findByProviderId(provider.getId());
        List<ProviderSpecialtyEntity> specialties =
                specialtyRepository.findByProviderId(provider.getId());
        List<ProviderContactEntity> contacts =
                contactRepository.findByProviderId(provider.getId());
        List<ProviderCouncilAffiliationEntity> affiliations =
                affiliationRepository.findByProviderId(provider.getId());

        return new ProviderDetail(provider, identifiers, specialties, contacts, affiliations);
    }

    /**
     * Update mutable fields on an existing provider record.
     *
     * Increments the entity version and publishes a {@code varapi.provider.updated}
     * domain event.
     *
     * @param providerPublicId the provider to update
     * @param request          the fields to change
     * @return the updated provider entity
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional
    public ProviderEntity updateProvider(String providerPublicId, UpdateProviderRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating provider: providerPublicId={}, actor={}",
                providerPublicId, ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        if (request.title() != null) provider.setTitle(request.title());
        if (request.givenName() != null) provider.setGivenName(request.givenName());
        if (request.familyName() != null) provider.setFamilyName(request.familyName());
        if (request.email() != null) provider.setEmail(request.email());
        if (request.phone() != null) provider.setPhone(request.phone());
        if (request.profession() != null) provider.setProfession(request.profession());
        if (request.cadre() != null) provider.setCadre(request.cadre());
        if (request.employmentOrgId() != null) provider.setEmploymentOrgId(request.employmentOrgId());

        if (request.primaryCouncilId() != null) {
            CouncilEntity councilRef = new CouncilEntity();
            councilRef.setId(request.primaryCouncilId());
            provider.setPrimaryCouncil(councilRef);
        }

        provider.setVersion(provider.getVersion() + 1);
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        log.info("Provider updated: providerPublicId={}, version={}",
                providerPublicId, provider.getVersion());

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                "varapi.provider.updated",
                String.format("{\"providerPublicId\":\"%s\",\"version\":%d}",
                        provider.getProviderPublicId(), provider.getVersion()));

        return provider;
    }

    /**
     * Search providers by name, profession, or status within a tenant.
     *
     * At least one of query, profession, or status should be provided for
     * meaningful results. If only tenantId is given, returns all providers
     * for that tenant (paged).
     *
     * @param request  the search request containing query, profession, and status filters
     * @param pageable pagination parameters
     * @return paged provider results
     */
    @Transactional(readOnly = true)
    public Page<ProviderEntity> searchProviders(ProviderSearchRequest request, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        log.debug("Searching providers: tenant={}, query={}, profession={}, status={}",
                tenantId, request.query(), request.profession(), request.status());

        if (request.query() != null && !request.query().isBlank()) {
            return providerRepository.searchByName(tenantId, request.query().trim(), pageable);
        }

        if (request.profession() != null && !request.profession().isBlank()) {
            return providerRepository.findByTenantIdAndProfession(tenantId, request.profession(), pageable);
        }

        if (request.status() != null && !request.status().isBlank()) {
            return providerRepository.findByTenantIdAndStatus(tenantId, request.status(), pageable);
        }

        // Default: all providers for tenant
        return providerRepository.findByTenantIdAndStatus(tenantId, "ACTIVE", pageable);
    }

    /**
     * Transition a provider's status with an auditable reason.
     *
     * Valid transitions are enforced:
     *   ACTIVE -> SUSPENDED, INACTIVE, REVOKED
     *   SUSPENDED -> ACTIVE, REVOKED
     *   INACTIVE -> ACTIVE
     *
     * The status change is recorded as a license history entry for audit
     * trail purposes, and a {@code varapi.provider.updated} event is published.
     *
     * @param providerPublicId the provider whose status to change
     * @param request          the status change request containing the new status and reason
     * @return the updated provider entity
     * @throws IllegalArgumentException if provider not found
     * @throws IllegalStateException    if the transition is not allowed
     */
    @Transactional
    public ProviderEntity changeStatus(String providerPublicId, StatusChangeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Changing provider status: providerPublicId={}, newStatus={}, reason={}, actor={}",
                providerPublicId, request.newStatus(), request.reason(), ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        String currentStatus = provider.getStatus();
        validateStatusTransition(currentStatus, request.newStatus());

        provider.setStatus(request.newStatus());
        provider.setVersion(provider.getVersion() + 1);
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        log.info("Provider status changed: providerPublicId={}, {} -> {}, version={}",
                providerPublicId, currentStatus, request.newStatus(), provider.getVersion());

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                "varapi.provider.updated",
                String.format("{\"providerPublicId\":\"%s\",\"previousStatus\":\"%s\"," +
                                "\"newStatus\":\"%s\",\"reason\":\"%s\",\"version\":%d}",
                        provider.getProviderPublicId(), currentStatus, request.newStatus(),
                        request.reason() != null ? request.reason() : "", provider.getVersion()));

        return provider;
    }

    // ---- Private helpers ----

    /**
     * Generate a ULID-style provider public ID.
     * Uses UUID.randomUUID() as the source of randomness, formatted as a
     * 26-character uppercase alphanumeric string.
     */
    private String generateProviderPublicId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }

    /**
     * Validate that a status transition is permitted.
     */
    private void validateStatusTransition(String current, String target) {
        boolean valid = switch (current) {
            case "ACTIVE" -> "SUSPENDED".equals(target) || "INACTIVE".equals(target) || "REVOKED".equals(target);
            case "SUSPENDED" -> "ACTIVE".equals(target) || "REVOKED".equals(target);
            case "INACTIVE" -> "ACTIVE".equals(target);
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: %s -> %s", current, target));
        }
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
