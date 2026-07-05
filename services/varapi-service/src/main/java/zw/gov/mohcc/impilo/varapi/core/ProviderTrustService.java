package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.enums.OnboardingChannel;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderVerificationAttemptEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderVerificationAttemptRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Governs the platform provider trust ladder (IATG Wave 1).
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>Monotonic:</b> trust never goes down, except through the explicit
 *       evidence-revoked path — {@code evidence.type == EVIDENCE_REVOKED} with a
 *       non-blank reason in {@code evidence.ref}.</li>
 *   <li><b>Evidenced:</b> EVERY transition (upgrade, reaffirmation, revoke) writes one
 *       {@link ProviderVerificationAttemptEntity} row.</li>
 *   <li><b>Coherent registry answer:</b> {@code registryStatus} is updated alongside
 *       the trust level — employment evidence yields {@code MATCHED_EMPLOYMENT_ONLY},
 *       council evidence yields {@code REGISTERED_ACTIVE}, and the composition of the
 *       two yields {@code MATCHED_BOTH}; a revoke drops back to
 *       {@code PENDING_VERIFICATION}.</li>
 *   <li><b>Council numbers are matching evidence, not identity:</b> they never leave
 *       this service in API-facing payloads (masking happens at the DTO boundary in
 *       {@code ProviderTrustController}).</li>
 * </ul>
 */
@Service
public class ProviderTrustService {

    private static final Logger log = LoggerFactory.getLogger(ProviderTrustService.class);

    // Evidence sources (frozen contract vocabulary — WS-D/WS-F depend on these).
    public static final String SOURCE_COUNCIL_RECORD = "COUNCIL_RECORD";
    public static final String SOURCE_HSC_EMPLOYMENT = "HSC_EMPLOYMENT";
    public static final String SOURCE_DOCUMENT = "DOCUMENT";
    public static final String SOURCE_ORG_ATTESTATION = "ORG_ATTESTATION";
    public static final Set<String> ALLOWED_EVIDENCE_SOURCES =
            Set.of(SOURCE_COUNCIL_RECORD, SOURCE_HSC_EMPLOYMENT, SOURCE_DOCUMENT, SOURCE_ORG_ATTESTATION);

    /** Evidence type that unlocks the (only) downgrade path; reason goes in evidence.ref. */
    public static final String EVIDENCE_TYPE_REVOKED = "EVIDENCE_REVOKED";

    // Transition outcomes recorded on the attempt trail.
    public static final String OUTCOME_TRUST_UPGRADED = "TRUST_UPGRADED";
    public static final String OUTCOME_TRUST_REAFFIRMED = "TRUST_REAFFIRMED";
    public static final String OUTCOME_TRUST_REVOKED = "TRUST_REVOKED";

    /** Immutable evidence for a trust transition. */
    public record TrustEvidence(String type, String source, String ref, BigDecimal confidence) {}

    /** Result of a transition — the provider now carries the new axes. */
    public record TrustTransitionResult(ProviderEntity provider,
                                        ProviderTrustLevel trustLevel,
                                        ProviderRegistryStatus registryStatus) {}

    private final ProviderRepository providerRepository;
    private final ProviderVerificationAttemptRepository attemptRepository;
    private final EventOutboxRepository outboxRepository;

    public ProviderTrustService(ProviderRepository providerRepository,
                                ProviderVerificationAttemptRepository attemptRepository,
                                EventOutboxRepository outboxRepository) {
        this.providerRepository = providerRepository;
        this.attemptRepository = attemptRepository;
        this.outboxRepository = outboxRepository;
    }

    // ── Transitions ──────────────────────────────────────────────────────────

    /**
     * Apply a trust transition. Upgrades and reaffirmations require valid evidence;
     * downgrades additionally require the explicit evidence-revoked path
     * ({@code evidence.type == EVIDENCE_REVOKED}, reason in {@code evidence.ref}).
     * Every call that succeeds writes exactly one verification-attempt row.
     */
    @Transactional
    public TrustTransitionResult applyTransition(String providerIdOrPublicId,
                                                 ProviderTrustLevel targetLevel,
                                                 TrustEvidence evidence,
                                                 OnboardingChannel channel) {
        TrustContext ctx = TrustContextHolder.require();
        if (targetLevel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetLevel is required");
        }
        validateEvidence(evidence);

        ProviderEntity provider = resolveProvider(ctx, providerIdOrPublicId);
        ProviderTrustLevel current = currentTrustLevel(provider);
        ProviderRegistryStatus currentRegistry = currentRegistryStatus(provider);

        final boolean downgrade = targetLevel.ordinal() < current.ordinal();
        final String outcome;
        final ProviderRegistryStatus nextRegistry;

        if (downgrade) {
            // The ONLY downgrade path: explicit evidence revocation with a reason.
            if (!EVIDENCE_TYPE_REVOKED.equalsIgnoreCase(evidence.type())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Trust is monotonic: downgrade from " + current + " to " + targetLevel
                                + " requires the explicit evidence-revoked path (evidence.type=EVIDENCE_REVOKED)");
            }
            if (evidence.ref() == null || evidence.ref().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Evidence revocation requires a reason in evidence.ref");
            }
            outcome = OUTCOME_TRUST_REVOKED;
            // Withdrawn evidence means the registry can no longer vouch: back to honest uncertainty.
            nextRegistry = ProviderRegistryStatus.PENDING_VERIFICATION;
        } else {
            if (EVIDENCE_TYPE_REVOKED.equalsIgnoreCase(evidence.type())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "EVIDENCE_REVOKED cannot accompany a trust upgrade");
            }
            outcome = targetLevel == current ? OUTCOME_TRUST_REAFFIRMED : OUTCOME_TRUST_UPGRADED;
            nextRegistry = composeRegistryStatus(currentRegistry, current, targetLevel, evidence.source());
        }

        provider.setTrustLevel(targetLevel.name());
        provider.setRegistryStatus(nextRegistry.name());
        if (channel != null && provider.getOnboardingChannel() == null) {
            provider.setOnboardingChannel(channel.name());
        }
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        recordAttemptRow(ctx, provider, evidence.type(), evidence.source(), outcome,
                evidence.confidence(), evidence.ref());

        publishEvent(provider, String.format(
                "{\"providerPublicId\":\"%s\",\"trustLevel\":\"%s\",\"registryStatus\":\"%s\","
                        + "\"previousTrustLevel\":\"%s\",\"outcome\":\"%s\"}",
                provider.getProviderPublicId(), targetLevel.name(), nextRegistry.name(),
                current.name(), outcome));

        log.info("provider trust transition: provider={} {} -> {} registryStatus={} outcome={}",
                provider.getProviderPublicId(), current, targetLevel, nextRegistry, outcome);
        return new TrustTransitionResult(provider, targetLevel, nextRegistry);
    }

    /**
     * Record a raw verification attempt without moving the trust level (evidence
     * gathering that has not, or not yet, produced a transition).
     */
    @Transactional
    public ProviderVerificationAttemptEntity recordAttempt(String providerIdOrPublicId,
                                                           String type,
                                                           String source,
                                                           String ref,
                                                           String outcome,
                                                           BigDecimal confidence) {
        TrustContext ctx = TrustContextHolder.require();
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        validateSource(source);
        if (outcome == null || outcome.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome is required");
        }
        ProviderEntity provider = resolveProvider(ctx, providerIdOrPublicId);
        return recordAttemptRow(ctx, provider, type.trim(), normalizeSource(source),
                outcome.trim(), confidence, ref);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProviderEntity getProvider(String providerIdOrPublicId) {
        return resolveProvider(TrustContextHolder.require(), providerIdOrPublicId);
    }

    @Transactional(readOnly = true)
    public List<ProviderVerificationAttemptEntity> listAttempts(ProviderEntity provider) {
        return attemptRepository.findByTenantIdAndProvider_IdOrderByAttemptedAtDescIdDesc(
                provider.getTenantId(), provider.getId());
    }

    // ── Registry-status composition ──────────────────────────────────────────

    /**
     * The honest registry answer after new evidence:
     * <ul>
     *   <li>council evidence at {@code COUNCIL_VERIFIED}+ &rarr; {@code REGISTERED_ACTIVE},
     *       or {@code MATCHED_BOTH} when employment evidence already matched;</li>
     *   <li>employment evidence at {@code EMPLOYMENT_MATCHED}+ &rarr;
     *       {@code MATCHED_EMPLOYMENT_ONLY}, or {@code MATCHED_BOTH} when council
     *       evidence already matched;</li>
     *   <li>document / org-attestation evidence never fabricates a registry match — the
     *       current answer stands (defaulting to {@code PENDING_VERIFICATION}).</li>
     * </ul>
     */
    private static ProviderRegistryStatus composeRegistryStatus(ProviderRegistryStatus current,
                                                                ProviderTrustLevel currentTrust,
                                                                ProviderTrustLevel target,
                                                                String source) {
        boolean hasEmployment = current == ProviderRegistryStatus.MATCHED_EMPLOYMENT_ONLY
                || current == ProviderRegistryStatus.MATCHED_BOTH;
        boolean hasCouncil = current == ProviderRegistryStatus.MATCHED_BOTH
                || current == ProviderRegistryStatus.REGISTERED_ACTIVE
                || currentTrust.ordinal() >= ProviderTrustLevel.COUNCIL_VERIFIED.ordinal();

        String s = normalizeSource(source);
        if (SOURCE_COUNCIL_RECORD.equals(s)
                && target.ordinal() >= ProviderTrustLevel.COUNCIL_VERIFIED.ordinal()) {
            return hasEmployment ? ProviderRegistryStatus.MATCHED_BOTH
                                 : ProviderRegistryStatus.REGISTERED_ACTIVE;
        }
        if (SOURCE_HSC_EMPLOYMENT.equals(s)
                && target.ordinal() >= ProviderTrustLevel.EMPLOYMENT_MATCHED.ordinal()) {
            return hasCouncil ? ProviderRegistryStatus.MATCHED_BOTH
                              : ProviderRegistryStatus.MATCHED_EMPLOYMENT_ONLY;
        }
        return current != null ? current : ProviderRegistryStatus.PENDING_VERIFICATION;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProviderEntity resolveProvider(TrustContext ctx, String providerIdOrPublicId) {
        if (providerIdOrPublicId == null || providerIdOrPublicId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }
        String key = providerIdOrPublicId.trim();
        java.util.Optional<ProviderEntity> found;
        if (key.chars().allMatch(Character::isDigit)) {
            found = providerRepository.findByIdAndTenantId(Long.parseLong(key), ctx.tenantId());
        } else {
            found = providerRepository.findByProviderPublicIdAndTenantId(key, ctx.tenantId());
        }
        return found.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown provider"));
    }

    private ProviderTrustLevel currentTrustLevel(ProviderEntity provider) {
        if (provider.getTrustLevel() == null || provider.getTrustLevel().isBlank()) {
            return ProviderTrustLevel.SELF_ASSERTED;
        }
        try {
            return ProviderTrustLevel.valueOf(provider.getTrustLevel().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("provider {} carries unknown trust_level '{}', treating as SELF_ASSERTED",
                    provider.getProviderPublicId(), provider.getTrustLevel());
            return ProviderTrustLevel.SELF_ASSERTED;
        }
    }

    private ProviderRegistryStatus currentRegistryStatus(ProviderEntity provider) {
        if (provider.getRegistryStatus() == null || provider.getRegistryStatus().isBlank()) {
            return null;
        }
        try {
            return ProviderRegistryStatus.valueOf(provider.getRegistryStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void validateEvidence(TrustEvidence evidence) {
        if (evidence == null || evidence.type() == null || evidence.type().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "evidence.type is required for every trust transition");
        }
        validateSource(evidence.source());
    }

    private static void validateSource(String source) {
        if (source == null || !ALLOWED_EVIDENCE_SOURCES.contains(normalizeSource(source))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "evidence.source must be one of " + ALLOWED_EVIDENCE_SOURCES);
        }
    }

    private static String normalizeSource(String source) {
        return source == null ? null : source.trim().toUpperCase(Locale.ROOT);
    }

    private ProviderVerificationAttemptEntity recordAttemptRow(TrustContext ctx,
                                                               ProviderEntity provider,
                                                               String type,
                                                               String source,
                                                               String outcome,
                                                               BigDecimal confidence,
                                                               String ref) {
        ProviderVerificationAttemptEntity row = new ProviderVerificationAttemptEntity();
        row.setTenantId(ctx.tenantId());
        row.setProvider(provider);
        row.setEvidenceType(type);
        row.setEvidenceSource(source);
        row.setOutcome(outcome);
        row.setConfidence(confidence);
        row.setEvidenceRef(ref);
        row.setAttemptedBy(ctx.actorId());
        return attemptRepository.save(row);
    }

    private void publishEvent(ProviderEntity provider, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PROVIDER");
        event.setAggregateId(provider.getProviderPublicId());
        event.setEventType("varapi.provider.trust_level_changed");
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
