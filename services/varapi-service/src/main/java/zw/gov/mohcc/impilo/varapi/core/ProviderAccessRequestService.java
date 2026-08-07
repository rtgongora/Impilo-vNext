package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.SubmitProviderAccessRequest;
import zw.gov.mohcc.impilo.varapi.enums.ProviderAccessRequestStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderAccessRequestType;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAccessRequestEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAccessRequestRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Durable self-service provider-access requests (IATG Journey D).
 *
 * <p>Honest by construction: every submission persists with a status and a named
 * next actor. New Provider IDs are never minted here; a request that cannot be
 * self-resolved lands in a PENDING_* state (or DUPLICATE_SUSPECTED when a profile
 * already appears linked to the applicant's Health ID — recover, do not reissue).</p>
 */
@Service
public class ProviderAccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(ProviderAccessRequestService.class);

    private final ProviderAccessRequestRepository requestRepository;
    private final EventOutboxRepository outboxRepository;
    private final ProviderRepository providerRepository;
    private final ProviderBootstrapService bootstrapService;

    public ProviderAccessRequestService(ProviderAccessRequestRepository requestRepository,
                                        EventOutboxRepository outboxRepository,
                                        ProviderRepository providerRepository,
                                        ProviderBootstrapService bootstrapService) {
        this.requestRepository = requestRepository;
        this.outboxRepository = outboxRepository;
        this.providerRepository = providerRepository;
        this.bootstrapService = bootstrapService;
    }

    /**
     * Turn an approved council-number claim into an actual claim, or explain in the record why it
     * could not be.
     *
     * <p>The decision itself is already committed by the time this runs, and that ordering is
     * deliberate: a reviewer's verdict is their act, and it stands even if the binding cannot be
     * completed. What must never happen is the reverse — a request that reads APPROVED while the
     * provider is silently still unclaimed, with nothing on the record saying so.</p>
     *
     * <p>So a failure here is written back onto the request as a reason and the status is moved to
     * NEEDS_MORE_INFORMATION, putting it back in front of a human, rather than swallowed. The
     * realistic causes are a profile claimed by someone else in the meantime and the
     * one-person-one-profile guard, both of which are decisions a person has to make.</p>
     */
    private void completeClaimIfCouncilNumber(ProviderAccessRequestEntity entity) {
        if (!ProviderAccessRequestType.COUNCIL_NUMBER.name().equals(entity.getRequestType())) {
            return;
        }
        String providerPublicId = trimToNull(entity.getProviderPublicId());
        if (providerPublicId == null || entity.getApplicantHealthId() == null) {
            // Nothing was resolved to grant — an approved new-registration enquiry, not a claim.
            return;
        }
        try {
            bootstrapService.completeClaimByReview(
                    providerPublicId, entity.getApplicantHealthId(), entity.getPublicId());
            log.info("Provider access request {} approved and claim completed for provider {}",
                    entity.getPublicId(), providerPublicId);
        } catch (RuntimeException ex) {
            log.warn("Provider access request {} approved but the claim could not be completed: {}",
                    entity.getPublicId(), ex.getMessage());
            entity.setStatus(ProviderAccessRequestStatus.NEEDS_MORE_INFORMATION.name());
            entity.setNextActor("REVIEWER");
            entity.setReason("Approved, but the profile could not be bound: " + ex.getMessage()
                    + " — this needs a person to resolve before the practitioner can participate.");
            requestRepository.save(entity);
        }
    }

    @Transactional
    public ProviderAccessRequestEntity submit(SubmitProviderAccessRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        UUID applicant = requireApplicant(ctx.actorId());
        ProviderAccessRequestType type = parseType(req.requestType());

        ProviderAccessRequestEntity entity = new ProviderAccessRequestEntity();
        entity.setPublicId(newPublicId());
        entity.setTenantId(tenantId);
        entity.setApplicantHealthId(applicant);
        entity.setRequestType(type.name());
        entity.setCreatedBy(ctx.actorId());
        entity.setProfession(trimToNull(req.profession()));
        entity.setCouncilCode(trimToNull(req.councilCode()));
        entity.setCouncilNumberMasked(mask(req.councilNumber()));
        entity.setEcNumberMasked(mask(req.ecNumber()));
        entity.setOrganizationRef(trimToNull(req.organizationRef()));
        entity.setEvidenceSummary(trimToNull(req.evidenceSummary()));
        entity.setFacilityId(req.facilityId());
        entity.setEngagementType(trimToNull(req.engagementType()) != null
                ? req.engagementType().trim().toUpperCase(Locale.ROOT) : null);
        entity.setAccessValidFrom(req.accessValidFrom());
        entity.setAccessValidTo(req.accessValidTo());

        if (type == ProviderAccessRequestType.FACILITY_ACCESS) {
            // Facility access rides on an EXISTING professional identity (PJ4):
            // the request needs a facility, an engagement type, an expiry for
            // temporary engagements, and a linked provider profile.
            if (entity.getFacilityId() == null) {
                throw new IllegalArgumentException("A facility is required for a facility-access request");
            }
            String engagement = entity.getEngagementType();
            if (engagement == null || !java.util.Set.of("PERMANENT", "ROTATION", "LOCUM", "OUTREACH",
                    "TELEMED", "SPECIALIST_POOL", "SUPERVISORY", "TRAINING").contains(engagement)) {
                throw new IllegalArgumentException("Unknown engagement type for a facility-access request");
            }
            if (!"PERMANENT".equals(engagement) && entity.getAccessValidTo() == null) {
                throw new IllegalArgumentException("A temporary engagement requires an access end date");
            }
        }

        // recover-not-reissue guard at the request layer: never create a "new" request
        // when the applicant already appears to hold a provider profile.
        boolean alreadyLinked = providerRepository.findByTenantIdAndImpiloHealthId(tenantId, applicant).isPresent();
        if (alreadyLinked && (type == ProviderAccessRequestType.NEW_PROVIDER)) {
            entity.setStatus(ProviderAccessRequestStatus.DUPLICATE_SUSPECTED.name());
            entity.setNextActor("NATIONAL_ADMINISTRATOR");
            entity.setReason("A provider profile already appears linked to this Health ID. "
                    + "Recover the existing Provider ID instead of requesting a new one.");
        } else {
            applyRouting(entity, type);
        }

        entity = requestRepository.save(entity);
        publishEvent("PROVIDER_ACCESS_REQUEST", entity.getPublicId(),
                "varapi.provider.access_request.submitted",
                String.format("{\"publicId\":\"%s\",\"requestType\":\"%s\",\"status\":\"%s\",\"nextActor\":\"%s\"}",
                        entity.getPublicId(), entity.getRequestType(), entity.getStatus(),
                        entity.getNextActor() == null ? "" : entity.getNextActor()),
                tenantId, ctx.correlationId());
        log.info("Provider access request {} submitted (type={}, status={}, nextActor={})",
                entity.getPublicId(), entity.getRequestType(), entity.getStatus(), entity.getNextActor());
        return entity;
    }

    @Transactional(readOnly = true)
    public List<ProviderAccessRequestEntity> listForApplicant() {
        TrustContext ctx = TrustContextHolder.require();
        return requestRepository.findByTenantIdAndApplicantHealthIdOrderByCreatedAtDescIdDesc(
                ctx.tenantId(), requireApplicant(ctx.actorId()));
    }

    @Transactional(readOnly = true)
    public ProviderAccessRequestEntity get(String publicId) {
        TrustContext ctx = TrustContextHolder.require();
        return requestRepository.findByTenantIdAndPublicId(ctx.tenantId(), publicId)
                .orElseThrow(() -> new IllegalArgumentException("No provider access request " + publicId));
    }

    // ── Reviewer lane (IATG Trust Console) ─────────────────────────────────────

    /**
     * Actor types permitted to read the reviewer queue or decide a request.
     *
     * <p>Matches the set already used for the two closest operations — claiming a preloaded
     * profile and recovering one ({@code ProviderBootstrapService}, {@code ProviderRecoveryService},
     * both {@code SYSTEM} + {@code REGISTRY_ADMIN}) — plus {@code NATIONAL_ADMIN}, which is the
     * actor behind this service's own {@code NATIONAL_ADMINISTRATOR} next-actor routing and is
     * already trusted with provider badges.</p>
     *
     * <p>{@code ORG_REPRESENTATIVE} and {@code OPERATOR} are deliberately excluded. An
     * organisation representative vouching for someone joining their organisation is a different
     * act from confirming that a person <em>is</em> the practitioner on the HPA register, and
     * {@link #decide} is a single code path across every request type — so the narrowest set
     * governs all of them. Letting an org representative decide organisation-scoped requests is a
     * per-type refinement worth making deliberately, not by leaving the gate wide now.</p>
     */
    static final Set<String> REVIEWER_ACTOR_TYPES = Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN");

    /**
     * Defence in depth behind ext_authz, in the idiom this service already uses elsewhere.
     *
     * <p>Until now the reviewer lane had <b>no in-service authorisation at all</b> — not on the
     * queue read, not on the decision. The only gate was the ext_authz policy pair
     * ({@code trust-console-varapi-review-system-admin} / {@code -hie-admin}), and the queue
     * endpoint's own javadoc said so. That gate is real, but it only covers traffic routed through
     * Envoy; anything reaching this service inside the cluster arrived unchecked.</p>
     *
     * <p>It became urgent when approval stopped being inert. An APPROVED council-number claim now
     * binds a provider profile to a person's Health ID, so an unauthorised decision grants a
     * professional identity — the strongest thing this service can hand out.</p>
     *
     * <p><b>Known vocabulary gap, stated rather than papered over.</b> The ext_authz rules gate on
     * <em>role</em> ({@code SYSTEM_ADMIN}, {@code HIE_ADMIN}); this check gates on
     * <em>actor type</em>. They are different dimensions, and the service cannot see the first:
     * {@code TrustContext} carries no roles, and no role header exists in the trust contract or in
     * Envoy's {@code allowed_upstream_headers}. Two gates keyed on two vocabularies can drift, and
     * closing that means carrying an authoritative role claim through to services — a trust-plane
     * change, not a varapi one. Until then the layers are deliberately different rather than
     * accidentally so.</p>
     *
     * <p>Fails closed, and says exactly what is required, because the first person to hit this
     * will be a reviewer whose identity has not been provisioned with a matching actor type — and
     * a bare 403 would send them looking in the wrong place.</p>
     */
    private static void requireReviewer(TrustContext ctx, String operation) {
        String actorType = ctx.actorType() != null
                ? ctx.actorType().trim().toUpperCase(Locale.ROOT) : "";
        if (!REVIEWER_ACTOR_TYPES.contains(actorType)) {
            log.warn("Refused {} — actor {} has actorType '{}', which is not a reviewer type",
                    operation, ctx.actorId(), actorType.isEmpty() ? "<absent>" : actorType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Reviewing provider access requests requires one of "
                            + REVIEWER_ACTOR_TYPES.stream().sorted().toList()
                            + "; this session presents "
                            + (actorType.isEmpty() ? "no actor type" : "'" + actorType + "'"));
        }
    }

    /** Statuses a reviewer may still decide from. Terminal states are never re-decided. */
    static final Set<String> DECIDABLE_STATUSES = Set.of(
            ProviderAccessRequestStatus.SUBMITTED.name(),
            ProviderAccessRequestStatus.PENDING_COUNCIL_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_EMPLOYER_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_ORGANIZATION_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_NATIONAL_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_FACILITY_REVIEW.name(),
            ProviderAccessRequestStatus.NEEDS_MORE_INFORMATION.name(),
            ProviderAccessRequestStatus.NEEDS_ADJUDICATION.name());

    /** Decisions a reviewer can record. NEEDS_MORE_INFORMATION keeps the request decidable. */
    static final Set<String> ALLOWED_DECISIONS = Set.of(
            ProviderAccessRequestStatus.APPROVED.name(),
            ProviderAccessRequestStatus.REJECTED.name(),
            ProviderAccessRequestStatus.NEEDS_MORE_INFORMATION.name());

    /**
     * Review queue: tenant-scoped requests in the given statuses (defaults to every
     * still-decidable status when none supplied), newest first.
     */
    @Transactional(readOnly = true)
    public List<ProviderAccessRequestEntity> listForReview(List<String> statuses) {
        TrustContext ctx = TrustContextHolder.require();
        // The queue is tenant-wide and carries other people's applicant Health IDs, masked council
        // numbers and evidence summaries. It is never applicant-facing.
        requireReviewer(ctx, "read of the provider access-request review queue");
        List<String> effective = (statuses == null || statuses.isEmpty())
                ? List.copyOf(DECIDABLE_STATUSES)
                : statuses.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
        return requestRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(ctx.tenantId(), effective);
    }

    /**
     * Record a reviewer decision. Allowed only from a still-decidable status
     * (SUBMITTED / PENDING_*_REVIEW / NEEDS_*) to APPROVED, REJECTED or
     * NEEDS_MORE_INFORMATION. The decision is auditable: who, when, why —
     * and emits the same outbox event pattern the submit path uses.
     */
    @Transactional
    public ProviderAccessRequestEntity decide(String publicId, String decision, String note) {
        TrustContext ctx = TrustContextHolder.require();
        // Checked before the request is even loaded: an unauthorised caller learns nothing about
        // whether a given public id exists.
        requireReviewer(ctx, "decision on a provider access request");
        ProviderAccessRequestEntity entity = requestRepository
                .findByTenantIdAndPublicId(ctx.tenantId(), publicId)
                .orElseThrow(() -> new IllegalArgumentException("No provider access request " + publicId));

        String target = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_DECISIONS.contains(target)) {
            throw new IllegalArgumentException(
                    "Unsupported decision '" + decision + "' — expected APPROVED, REJECTED or NEEDS_MORE_INFORMATION");
        }
        if (!DECIDABLE_STATUSES.contains(entity.getStatus())) {
            throw new IllegalStateException("Request " + publicId + " is in status " + entity.getStatus()
                    + " and can no longer be decided");
        }

        entity.setStatus(target);
        entity.setDecidedBy(ctx.actorId());
        entity.setDecidedAt(Instant.now());
        entity.setDecisionNote(trimToNull(note));
        if (ProviderAccessRequestStatus.NEEDS_MORE_INFORMATION.name().equals(target)) {
            entity.setNextActor("APPLICANT");
            entity.setReason(trimToNull(note) != null ? note.trim()
                    : "The reviewer needs more information before a decision can be made.");
        } else {
            entity.setNextActor(null);
            if (trimToNull(note) != null) {
                entity.setReason(note.trim());
            }
        }
        entity = requestRepository.save(entity);

        // ── An approval has to change something ──────────────────────────────────────────────
        // For a council-number claim, approving IS the grant: the reviewer has checked the
        // claimant against the council register, and that check is the only identity evidence
        // there is — a registration number is public, so matching one proves nothing on its own.
        //
        // Until this call existed the workflow stopped here. The request went to APPROVED, the
        // console showed it granted, and varapi.provider was never touched — so claimed_at stayed
        // null, and claimed_at is what booking reads. The practitioner stayed unbookable while
        // every surface said they had been approved. Measured before this change: 4,268 providers,
        // zero claimed, so not one provider in the estate could be booked.
        //
        // Deliberately narrow. Only COUNCIL_NUMBER claims bind a profile, and only when the
        // submission already resolved one — HpaPractitionerClaimService records providerPublicId
        // for the reviewer when the number matched a preloaded HPA row and leaves it null when it
        // did not. A null there means "no profile to grant", which is a legitimate approval of a
        // new-registration enquiry, not a failure.
        if (ProviderAccessRequestStatus.APPROVED.name().equals(target)) {
            completeClaimIfCouncilNumber(entity);
        }

        publishEvent("PROVIDER_ACCESS_REQUEST", entity.getPublicId(),
                "varapi.provider.access_request.decided",
                String.format("{\"publicId\":\"%s\",\"requestType\":\"%s\",\"status\":\"%s\",\"decidedBy\":\"%s\","
                                + "\"applicantHealthId\":\"%s\",\"facilityId\":\"%s\",\"engagementType\":\"%s\","
                                + "\"accessValidFrom\":\"%s\",\"accessValidTo\":\"%s\"}",
                        entity.getPublicId(), entity.getRequestType(), entity.getStatus(),
                        entity.getDecidedBy() == null ? "" : entity.getDecidedBy(),
                        entity.getApplicantHealthId() == null ? "" : entity.getApplicantHealthId(),
                        entity.getFacilityId() == null ? "" : entity.getFacilityId(),
                        entity.getEngagementType() == null ? "" : entity.getEngagementType(),
                        entity.getAccessValidFrom() == null ? "" : entity.getAccessValidFrom(),
                        entity.getAccessValidTo() == null ? "" : entity.getAccessValidTo()),
                ctx.tenantId(), ctx.correlationId());
        log.info("Provider access request {} decided (status={}, decidedBy={})",
                entity.getPublicId(), entity.getStatus(), entity.getDecidedBy());
        return entity;
    }

    /** Route a fresh request to its pending stage + next actor by the evidence supplied. */
    private void applyRouting(ProviderAccessRequestEntity entity, ProviderAccessRequestType type) {
        switch (type) {
            case FACILITY_ACCESS -> {
                entity.setStatus(ProviderAccessRequestStatus.PENDING_FACILITY_REVIEW.name());
                entity.setNextActor("FACILITY_ADMINISTRATOR");
                entity.setReason("Awaiting approval by the receiving facility. Work access begins only after the facility approves and the posting is recorded.");
            }
            case ORG_INVITATION -> {
                entity.setStatus(ProviderAccessRequestStatus.PENDING_ORGANIZATION_REVIEW.name());
                entity.setNextActor("ORGANIZATION_REPRESENTATIVE");
                entity.setReason("Awaiting confirmation by the inviting organization before any provider access is granted.");
            }
            case COUNCIL_NUMBER -> {
                entity.setStatus(ProviderAccessRequestStatus.PENDING_COUNCIL_REVIEW.name());
                entity.setNextActor("COUNCIL_REVIEWER");
                entity.setReason("Submitted for council verification. No Provider ID is issued until the council confirms the registration.");
            }
            case EC_NUMBER -> {
                entity.setStatus(ProviderAccessRequestStatus.PENDING_EMPLOYER_REVIEW.name());
                entity.setNextActor("EMPLOYER_HR_REVIEWER");
                entity.setReason("Submitted for public-sector employment verification.");
            }
            case NEW_PROVIDER -> {
                if (entity.getCouncilNumberMasked() != null) {
                    entity.setStatus(ProviderAccessRequestStatus.PENDING_COUNCIL_REVIEW.name());
                    entity.setNextActor("COUNCIL_REVIEWER");
                } else if (entity.getEcNumberMasked() != null) {
                    entity.setStatus(ProviderAccessRequestStatus.PENDING_EMPLOYER_REVIEW.name());
                    entity.setNextActor("EMPLOYER_HR_REVIEWER");
                } else {
                    entity.setStatus(ProviderAccessRequestStatus.PENDING_NATIONAL_REVIEW.name());
                    entity.setNextActor("NATIONAL_ADMINISTRATOR");
                }
                entity.setReason("Submitted for verification. A Provider ID is never issued automatically for a new provider "
                        + "— it requires the named reviewer's decision (or adjudication).");
            }
            default -> {
                entity.setStatus(ProviderAccessRequestStatus.SUBMITTED.name());
                entity.setNextActor("SELF_SERVICE");
                entity.setReason("Recorded. Use the matching claim or recovery lane to complete this request.");
            }
        }
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload,
                              UUID tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId == null ? null : tenantId.toString());
        event.setCorrelationId(correlationId == null ? null : correlationId.toString());
        outboxRepository.save(event);
    }

    private String newPublicId() {
        for (int i = 0; i < 5; i++) {
            String candidate = "PAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!requestRepository.existsByPublicId(candidate)) {
                return candidate;
            }
        }
        return "PAR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private static UUID requireApplicant(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("An authenticated person (X-Actor-ID) is required to request provider access");
        }
        try {
            return UUID.fromString(actorId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The person anchor (X-Actor-ID) must be a Health ID UUID");
        }
    }

    private static ProviderAccessRequestType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("requestType is required");
        }
        try {
            return ProviderAccessRequestType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown requestType: " + raw);
        }
    }

    /** First four characters + {@code ***}; never emit a raw council/EC number. */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 4 ? trimmed + "***" : trimmed.substring(0, 4) + "***";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
