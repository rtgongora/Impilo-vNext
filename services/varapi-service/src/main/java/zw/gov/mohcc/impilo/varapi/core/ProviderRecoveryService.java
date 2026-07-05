package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryCompleteResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryEvidence;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryInitiateResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderClaimTokenEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderClaimTokenRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Provider profile <b>recovery</b> (IATG WS-F): recover-not-reissue.
 *
 * <p>A person who already owns a provider profile (lifecycle CLAIMED) but has
 * lost their login binding re-establishes it here. Two-step, mirroring the
 * bootstrap self-claim pattern:</p>
 * <ol>
 *   <li><b>initiate</b> — evidence in hand, issue a single-use recovery-purpose
 *       token BOUND to the person's existing provider profile;</li>
 *   <li><b>complete</b> — atomically redeem the token and re-link the login,
 *       returning the SAME {@code providerPublicId} the profile always had.</li>
 * </ol>
 *
 * <p><b>No schema change:</b> recovery reuses {@link ProviderClaimTokenEntity}
 * unmodified. Purpose is encoded in the existing {@code issuedToHint} column
 * via the {@value #RECOVERY_HINT_PREFIX} prefix — {@link #complete(String)}
 * only redeems tokens carrying that prefix, so a bootstrap claim token can
 * never be replayed as a recovery token. The converse is also safe: a recovery
 * token pushed through the bootstrap claim path fails its
 * PRELOADED-lifecycle guard and the atomic redeem rolls back.</p>
 *
 * <p><b>Never mints:</b> this service never creates a {@link ProviderEntity}.
 * Unknown Health ID → 404. The single mutation is re-binding
 * {@code impiloHealthId} on the existing row; {@code providerPublicId} is
 * untouched.</p>
 */
@Service
public class ProviderRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ProviderRecoveryService.class);

    /** Purpose marker stored in the existing issued_to_hint column (no migration). */
    public static final String RECOVERY_HINT_PREFIX = "RECOVERY|";

    private static final long RECOVERY_TOKEN_TTL_HOURS = 24;
    private static final String LIFECYCLE_PRELOADED = "PRELOADED";
    private static final int HINT_MAX_LENGTH = 255;

    private final ProviderRepository providerRepository;
    private final ProviderClaimTokenRepository claimTokenRepository;
    private final EventOutboxRepository outboxRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProviderRecoveryService(ProviderRepository providerRepository,
                                   ProviderClaimTokenRepository claimTokenRepository,
                                   EventOutboxRepository outboxRepository) {
        this.providerRepository = providerRepository;
        this.claimTokenRepository = claimTokenRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Issue a single-use, recovery-purpose token bound to the EXISTING provider
     * profile anchored to {@code healthId}. 404 when no profile is linked — a
     * recovery can never create one (recover-not-reissue).
     */
    @Transactional
    public RecoveryInitiateResponse initiate(UUID healthId, RecoveryEvidence evidence) {
        TrustContext ctx = TrustContextHolder.require();
        if (healthId == null) {
            throw new IllegalArgumentException("healthId is required");
        }
        if (evidence == null || evidence.type() == null || evidence.type().isBlank()) {
            throw new IllegalArgumentException("evidence.type is required");
        }
        // Policy PROVIDER-SELF-RECOVERY (defence-in-depth behind ext_authz):
        // only the person themselves may initiate recovery of their own profile;
        // SYSTEM / REGISTRY_ADMIN excepted for assisted desk recovery.
        String actorType = ctx.actorType() != null ? ctx.actorType().trim().toUpperCase(Locale.ROOT) : "";
        if (!Set.of("SYSTEM", "REGISTRY_ADMIN").contains(actorType)
                && (ctx.actorId() == null || !healthId.toString().equalsIgnoreCase(ctx.actorId().trim()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A provider profile may only be recovered by the authenticated person it belongs to");
        }

        ProviderEntity provider = providerRepository
                .findByTenantIdAndImpiloHealthId(ctx.tenantId(), healthId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No provider profile is linked to this Health ID"));

        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(RECOVERY_TOKEN_TTL_HOURS, ChronoUnit.HOURS);

        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setTenantId(ctx.tenantId());
        token.setProviderId(provider.getId());
        token.setTokenHash(ProviderBootstrapService.hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setIssuedToHint(recoveryHint(evidence));
        token.setExpiresAt(expiresAt);
        token.setCreatedBy(ctx.actorId());
        claimTokenRepository.save(token);

        // Evidence ref is intentionally excluded from the event payload — only
        // its type/source travel; the ref may carry a council/EC number.
        publishEvent(provider.getProviderPublicId(), "varapi.provider.recovery.initiated",
                String.format("{\"providerPublicId\":\"%s\",\"evidenceType\":\"%s\",\"evidenceSource\":\"%s\"}",
                        provider.getProviderPublicId(), evidence.type(),
                        evidence.source() != null ? evidence.source() : ""));

        log.info("provider recovery initiated: providerPublicId={}", provider.getProviderPublicId());
        return new RecoveryInitiateResponse(provider.getProviderPublicId(), rawToken, expiresAt);
    }

    /**
     * Atomically redeem a recovery token and re-link the login: the
     * authenticated person's Health ID is re-bound to the SAME provider record
     * the token was issued for. {@code providerPublicId} never changes.
     */
    @Transactional
    public RecoveryCompleteResponse complete(String rawToken) {
        TrustContext ctx = TrustContextHolder.require();
        UUID actorHealthId = requireActorHealthId(ctx);

        ProviderClaimTokenEntity token = loadRedeemableRecoveryToken(ctx.tenantId(), rawToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Recovery token is invalid, expired, or already used"));

        Instant now = Instant.now();
        // Same atomic single-use guard as bootstrap claim: conditional UPDATE
        // ISSUED → CLAIMED; exactly one concurrent redemption wins.
        int redeemed = claimTokenRepository.redeem(token.getId(), now, actorHealthId);
        if (redeemed != 1) {
            throw new IllegalStateException("Recovery token is invalid, expired, or already used");
        }

        ProviderEntity provider = providerRepository
                .findByIdAndTenantId(token.getProviderId(), ctx.tenantId())
                .orElseThrow(() -> new IllegalStateException("Provider profile no longer exists"));

        if (LIFECYCLE_PRELOADED.equals(provider.getLifecycleStatus())) {
            // A PRELOADED skeleton has never been claimed — nothing to recover.
            throw new IllegalStateException(
                    "Provider profile has not been claimed yet; use the claim journey instead");
        }

        // One person, one provider profile: the recovering person may not
        // already own a DIFFERENT provider record.
        final Long recoveredProviderId = provider.getId();
        providerRepository.findByTenantIdAndImpiloHealthId(ctx.tenantId(), actorHealthId)
                .filter(p -> !p.getId().equals(recoveredProviderId))
                .ifPresent(p -> {
                    throw new IllegalStateException("This person already has a provider profile");
                });

        String providerPublicIdBefore = provider.getProviderPublicId();

        // Recover-not-reissue: re-bind the person anchor ONLY. providerPublicId,
        // lifecycle history, registrations and licences all stay on the same row.
        provider.setImpiloHealthId(actorHealthId);
        provider.setClaimedHealthId(actorHealthId);
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        if (!providerPublicIdBefore.equals(provider.getProviderPublicId())) {
            // Structural invariant — must never happen; fail loudly rather than
            // silently returning a different identity.
            throw new IllegalStateException("Recovery integrity violation: provider identifier changed");
        }

        publishEvent(provider.getProviderPublicId(), "varapi.provider.recovered",
                String.format("{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\"}",
                        provider.getProviderPublicId(), provider.getImpiloHealthId()));

        log.info("provider profile recovered: providerPublicId={}", provider.getProviderPublicId());
        return new RecoveryCompleteResponse(
                provider.getProviderPublicId(),
                provider.getImpiloHealthId(),
                provider.getLifecycleStatus());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<ProviderClaimTokenEntity> loadRedeemableRecoveryToken(UUID tenantId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return claimTokenRepository
                .findByTenantIdAndTokenHash(tenantId, ProviderBootstrapService.hash(rawToken.trim()))
                .filter(t -> t.isRedeemable(Instant.now()))
                // Purpose guard: only RECOVERY-purpose tokens are redeemable here.
                .filter(t -> t.getIssuedToHint() != null
                        && t.getIssuedToHint().startsWith(RECOVERY_HINT_PREFIX));
    }

    private UUID requireActorHealthId(TrustContext ctx) {
        try {
            return UUID.fromString(ctx.actorId().trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Recovery completion requires an authenticated person actor (Health ID)");
        }
    }

    private String generateRawToken() {
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String recoveryHint(RecoveryEvidence evidence) {
        StringBuilder hint = new StringBuilder(RECOVERY_HINT_PREFIX).append(evidence.type().trim());
        if (evidence.source() != null && !evidence.source().isBlank()) {
            hint.append('|').append(evidence.source().trim());
        }
        return hint.length() <= HINT_MAX_LENGTH ? hint.toString() : hint.substring(0, HINT_MAX_LENGTH);
    }

    private void publishEvent(String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PROVIDER");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
