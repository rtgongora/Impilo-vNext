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
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
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

import zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final VarapiProperties varapiProperties;

    public ProviderService(ProviderRepository providerRepository,
                           ProviderIdentifierRepository identifierRepository,
                           ProviderSpecialtyRepository specialtyRepository,
                           ProviderContactRepository contactRepository,
                           ProviderCouncilAffiliationRepository affiliationRepository,
                           EventOutboxRepository outboxRepository,
                           VarapiProperties varapiProperties) {
        this.providerRepository = providerRepository;
        this.identifierRepository = identifierRepository;
        this.specialtyRepository = specialtyRepository;
        this.contactRepository = contactRepository;
        this.affiliationRepository = affiliationRepository;
        this.outboxRepository = outboxRepository;
        this.varapiProperties = varapiProperties;
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

        UUID impiloHealthId = resolveImpiloHealthIdForCreate(request, ctx);
        providerRepository.findByTenantIdAndImpiloHealthId(ctx.tenantId(), impiloHealthId).ifPresent(p -> {
            throw new IllegalStateException("Provider profile already exists for Impilo ID: " + impiloHealthId);
        });

        ProviderEntity provider = new ProviderEntity();
        provider.setTenantId(ctx.tenantId());
        provider.setImpiloHealthId(impiloHealthId);
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
        // A freshly registered provider is REGISTERED on the canonical lifecycle axis;
        // status / active_flag / licence_status are derived from it (never set directly).
        provider.setLifecycleStatus("REGISTERED");
        provider.deriveStatusProjections();
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

        syncLinkedIdentifiersFromDemographics(provider, request);

        log.info("Provider created: providerPublicId={}, providerRef={}",
                provider.getProviderPublicId(), provider.getProviderRef());

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                "varapi.provider.created",
                String.format("{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\",\"givenName\":\"%s\"," +
                                "\"familyName\":\"%s\",\"profession\":\"%s\",\"status\":\"ACTIVE\"}",
                        provider.getProviderPublicId(),
                        provider.getImpiloHealthId(),
                        provider.getGivenName(),
                        provider.getFamilyName(),
                        provider.getProfession()));

        return provider;
    }

    private UUID resolveImpiloHealthIdForCreate(CreateProviderRequest request, TrustContext ctx) {
        boolean require = varapiProperties.getIdentity().isRequireImpiloHealthIdOnProviderCreate();
        UUID hid = request.impiloHealthId();
        if (hid == null) {
            if (require) {
                throw new IllegalArgumentException("impiloHealthId is required — resolve or allocate Health ID before registry create");
            }
            return UUID.randomUUID();
        }
        return hid;
    }

    private void syncLinkedIdentifiersFromDemographics(ProviderEntity provider, CreateProviderRequest request) {
        if (request.nationalId() != null && !request.nationalId().isBlank()) {
            upsertLinkedIdentifier(provider, "ZW_NATIONAL_ID", "NATIONAL_ID", request.nationalId(), false);
        }
        if (request.practiceNumber() != null && !request.practiceNumber().isBlank()) {
            upsertLinkedIdentifier(provider, "VARAPI_PRACTICE_NUMBER", "PRACTICE_NUMBER", request.practiceNumber(), false);
        }
        upsertLinkedIdentifier(provider, "VARAPI_PROVIDER_PUBLIC_ID", "PROVIDER_PUBLIC_ID",
                provider.getProviderPublicId(), true);
    }

    private void upsertLinkedIdentifier(
            ProviderEntity provider,
            String identifierSystem,
            String identifierType,
            String identifierValue,
            boolean primary) {
        Optional<ProviderIdentifierEntity> global = identifierRepository.findByIdentifierSystemAndIdentifierValue(
                identifierSystem, identifierValue);
        if (global.isPresent() && !global.get().getProvider().getId().equals(provider.getId())) {
            throw new IllegalStateException("Identifier already linked to another provider: " + identifierSystem);
        }
        ProviderIdentifierEntity row = global.orElseGet(() -> identifierRepository.findByProviderId(provider.getId()).stream()
                .filter(i -> identifierSystem.equals(i.getIdentifierSystem())
                        && identifierValue.equals(i.getIdentifierValue()))
                .findFirst()
                .orElseGet(ProviderIdentifierEntity::new));
        row.setTenantId(provider.getTenantId());
        row.setProvider(provider);
        row.setIdentifierSystem(identifierSystem);
        row.setIdentifierType(identifierType);
        row.setIdentifierValue(identifierValue);
        row.setVerificationState("UNVERIFIED");
        row.setPrimary(primary);
        row.setStatus("ACTIVE");
        identifierRepository.save(row);
    }

    /**
     * Full provider projection keyed by canonical Impilo / Health ID (UUID string or UUID).
     */
    @Transactional(readOnly = true)
    public ProviderDetail getProviderByImpiloHealthId(String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID hid = UUID.fromString(healthId.trim());
        ProviderEntity provider = providerRepository.findByTenantIdAndImpiloHealthId(ctx.tenantId(), hid)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found for Impilo ID: " + healthId));
        return getProvider(provider.getProviderPublicId());
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

        // Default: the whole register.
        //
        // This read findByTenantIdAndStatus(tenantId, "ACTIVE") under a comment claiming
        // "all providers for tenant" — the comment and the code disagreed, and the code won.
        //
        // Registration and participation are different things. A practitioner on the Health
        // Professions Authority roll is registered, and that alone is what makes them
        // searchable, verifiable and findable. ACTIVE only ever meant they had opted in to
        // receiving appointment and prescription requests. Filtering discovery on the opt-in
        // hid 4,241 registered practitioners: a browse returned zero and read as an empty
        // register rather than an unclaimed one.
        //
        // Opt-in still gates booking and request-routing. ProviderSummary carries
        // lifecycleStatus, registryStatus and claimed precisely so a caller can enforce that,
        // and so an unverified import is labelled rather than presented as established.
        // Discovery is not authorisation, and it is not endorsement either.
        return providerRepository.findByTenantId(tenantId, pageable);
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

        // Wave-2: the status axis is a derived projection. Move the CANONICAL
        // lifecycle_status to the value that projects back to the requested status,
        // then recompute status / active_flag / licence_status from it. The external
        // status vocabulary (ACTIVE/SUSPENDED/INACTIVE/REVOKED) is preserved.
        provider.setLifecycleStatus(ProviderEntity.lifecycleForStatusChange(request.newStatus()));
        provider.deriveStatusProjections();
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

    // ---- Canonical lifecycle transition console (G10) ----

    /**
     * Operator-invocable lifecycle transitions (curated subset). Application/bootstrap states
     * (PRELOADED/CLAIMED/DRAFT/APPLICATION_IN_PROGRESS/UNDER_VERIFICATION/PENDING_*) are driven
     * by their owning workflows and are read-only here; renewal arcs (LICENCE_DUE_FOR_RENEWAL /
     * RENEWAL_IN_PROGRESS) are driven by the G8 renewal flow. DECEASED/REMOVED are terminal.
     */
    private static final Map<ProviderLifecycleStatus, Set<ProviderLifecycleStatus>> LIFECYCLE_TRANSITIONS =
            new EnumMap<>(ProviderLifecycleStatus.class);
    static {
        LIFECYCLE_TRANSITIONS.put(ProviderLifecycleStatus.REGISTERED,
                EnumSet.of(ProviderLifecycleStatus.LICENCED_ACTIVE));
        LIFECYCLE_TRANSITIONS.put(ProviderLifecycleStatus.LICENCED_ACTIVE,
                EnumSet.of(ProviderLifecycleStatus.SUSPENDED, ProviderLifecycleStatus.RESTRICTED,
                        ProviderLifecycleStatus.RETIRED, ProviderLifecycleStatus.DECEASED));
        LIFECYCLE_TRANSITIONS.put(ProviderLifecycleStatus.SUSPENDED,
                EnumSet.of(ProviderLifecycleStatus.LICENCED_ACTIVE, ProviderLifecycleStatus.RESTRICTED,
                        ProviderLifecycleStatus.RETIRED, ProviderLifecycleStatus.REMOVED));
        LIFECYCLE_TRANSITIONS.put(ProviderLifecycleStatus.RESTRICTED,
                EnumSet.of(ProviderLifecycleStatus.LICENCED_ACTIVE, ProviderLifecycleStatus.SUSPENDED,
                        ProviderLifecycleStatus.RETIRED));
        LIFECYCLE_TRANSITIONS.put(ProviderLifecycleStatus.LAPSED,
                EnumSet.of(ProviderLifecycleStatus.RESTORATION_IN_PROGRESS, ProviderLifecycleStatus.RETIRED));
        LIFECYCLE_TRANSITIONS.put(ProviderLifecycleStatus.RESTORATION_IN_PROGRESS,
                EnumSet.of(ProviderLifecycleStatus.LICENCED_ACTIVE, ProviderLifecycleStatus.LAPSED));
    }

    private static final Set<ProviderLifecycleStatus> TERMINAL =
            EnumSet.of(ProviderLifecycleStatus.RETIRED, ProviderLifecycleStatus.DECEASED, ProviderLifecycleStatus.REMOVED);

    public record AllowedTransitions(String current, List<String> allowed) {}
    public record LifecycleTransitionResult(ProviderEntity provider, List<String> warnings) {}

    /** Current lifecycle status + the operator-invocable next states. */
    public AllowedTransitions allowedLifecycleTransitions(String providerPublicId) {
        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerPublicId));
        ProviderLifecycleStatus current = parseLifecycle(provider.getLifecycleStatus());
        Set<ProviderLifecycleStatus> next = current == null ? Set.of()
                : LIFECYCLE_TRANSITIONS.getOrDefault(current, Set.of());
        List<String> allowed = new ArrayList<>();
        next.forEach(s -> allowed.add(s.name()));
        return new AllowedTransitions(current == null ? provider.getLifecycleStatus() : current.name(), allowed);
    }

    /**
     * Apply a canonical lifecycle transition (suspend/restrict/lapse/restore/retire/deceased/remove).
     * Validates the transition, requires a reason for terminal states (and a source ref for DECEASED),
     * recomputes the derived status axes, and emits an outbox event.
     */
    @Transactional
    public LifecycleTransitionResult transitionLifecycle(String providerPublicId, String targetState,
                                                         String reason, String effectiveDate, String sourceRef) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerPublicId));

        ProviderLifecycleStatus current = parseLifecycle(provider.getLifecycleStatus());
        ProviderLifecycleStatus target = parseLifecycle(targetState);
        if (target == null) {
            throw new IllegalArgumentException("Unknown lifecycle status: " + targetState);
        }
        Set<ProviderLifecycleStatus> allowed = current == null ? Set.of()
                : LIFECYCLE_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Invalid lifecycle transition: "
                    + (current == null ? provider.getLifecycleStatus() : current.name()) + " -> " + target.name());
        }
        if (TERMINAL.contains(target) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required to transition a provider to " + target.name());
        }
        if (target == ProviderLifecycleStatus.DECEASED && (sourceRef == null || sourceRef.isBlank())) {
            throw new IllegalArgumentException("A source reference (e.g. death notification) is required to mark a provider DECEASED");
        }

        String previous = provider.getLifecycleStatus();
        provider.setLifecycleStatus(target.name());
        provider.deriveStatusProjections();
        provider.setVersion(provider.getVersion() + 1);
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        publishEvent("PROVIDER", provider.getProviderPublicId(), "varapi.provider.updated",
                String.format("{\"providerPublicId\":\"%s\",\"previousLifecycle\":\"%s\",\"newLifecycle\":\"%s\","
                                + "\"reason\":\"%s\",\"effectiveDate\":\"%s\",\"sourceRef\":\"%s\",\"version\":%d}",
                        provider.getProviderPublicId(), previous, target.name(),
                        reason != null ? reason : "", effectiveDate != null ? effectiveDate : "",
                        sourceRef != null ? sourceRef : "", provider.getVersion()));

        List<String> warnings = new ArrayList<>();
        if (TERMINAL.contains(target)) {
            warnings.add("Confirm a successor for any active practitioner-in-charge assignment before finalising.");
        }
        return new LifecycleTransitionResult(provider, warnings);
    }

    private static ProviderLifecycleStatus parseLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProviderLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
