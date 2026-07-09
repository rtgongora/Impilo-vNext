package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ProviderAccessRequestService(ProviderAccessRequestRepository requestRepository,
                                        EventOutboxRepository outboxRepository,
                                        ProviderRepository providerRepository) {
        this.requestRepository = requestRepository;
        this.outboxRepository = outboxRepository;
        this.providerRepository = providerRepository;
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

    /** Statuses a reviewer may still decide from. Terminal states are never re-decided. */
    static final Set<String> DECIDABLE_STATUSES = Set.of(
            ProviderAccessRequestStatus.SUBMITTED.name(),
            ProviderAccessRequestStatus.PENDING_COUNCIL_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_EMPLOYER_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_ORGANIZATION_REVIEW.name(),
            ProviderAccessRequestStatus.PENDING_NATIONAL_REVIEW.name(),
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

        publishEvent("PROVIDER_ACCESS_REQUEST", entity.getPublicId(),
                "varapi.provider.access_request.decided",
                String.format("{\"publicId\":\"%s\",\"requestType\":\"%s\",\"status\":\"%s\",\"decidedBy\":\"%s\"}",
                        entity.getPublicId(), entity.getRequestType(), entity.getStatus(),
                        entity.getDecidedBy() == null ? "" : entity.getDecidedBy()),
                ctx.tenantId(), ctx.correlationId());
        log.info("Provider access request {} decided (status={}, decidedBy={})",
                entity.getPublicId(), entity.getStatus(), entity.getDecidedBy());
        return entity;
    }

    /** Route a fresh request to its pending stage + next actor by the evidence supplied. */
    private void applyRouting(ProviderAccessRequestEntity entity, ProviderAccessRequestType type) {
        switch (type) {
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
