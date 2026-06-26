package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.BulkPreloadRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.BulkPreloadResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ClaimProfileResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderClaimTokenEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderClaimTokenRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider bootstrap chain (L3 W6): bulk preload + self-claim.
 *
 * <p>Bootstrap doctrine: national-admin → org → representatives → <b>bulk
 * provider preload</b> → provider <b>self-claim</b>. A regulator or org
 * representative preloads skeleton provider profiles in {@code PRELOADED}
 * lifecycle state; the real provider later authenticates as a person
 * (person-first login) and claims the skeleton, binding their verified Impilo
 * Health ID to it.</p>
 *
 * <p>Reuses the existing {@link ProviderEntity} (no shadow registry) and the
 * standard Varapi outbox pattern for reliable downstream events.</p>
 *
 * <p>Policy is enforced at the Tshepo ext_authz path (the routes are
 * ext_authz-gated). Authorisation that the caller is a national-admin / org rep
 * (preload) or the legitimate claimant (claim) is specced to track P:
 * TODO(policy PROVIDER-PRELOAD-ADMIN): only national-admin/org-rep may bulk-preload.
 * TODO(policy PROVIDER-SELF-CLAIM): claim requires claimantHealthId == authenticated actor.</p>
 */
@Service
public class ProviderBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(ProviderBootstrapService.class);

    private static final String ORIGIN_BULK_PRELOAD = "BULK_PRELOAD";
    private static final String LIFECYCLE_PRELOADED = "PRELOADED";
    private static final String LIFECYCLE_CLAIMED = "CLAIMED";
    /** Preloaded skeletons are not yet operational; status gates them out of active use. */
    private static final String STATUS_PRELOADED = "INACTIVE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final long CLAIM_TOKEN_TTL_DAYS = 30;

    private final ProviderRepository providerRepository;
    private final ProviderClaimTokenRepository claimTokenRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final CouncilRepository councilRepository;
    private final EventOutboxRepository outboxRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProviderBootstrapService(ProviderRepository providerRepository,
                                    ProviderClaimTokenRepository claimTokenRepository,
                                    ProviderCouncilRegistrationRecordRepository registrationRepository,
                                    CouncilRepository councilRepository,
                                    EventOutboxRepository outboxRepository) {
        this.providerRepository = providerRepository;
        this.claimTokenRepository = claimTokenRepository;
        this.registrationRepository = registrationRepository;
        this.councilRepository = councilRepository;
        this.outboxRepository = outboxRepository;
    }

    // ── Bulk preload ────────────────────────────────────────────────────────

    /**
     * Create a batch of PRELOADED provider skeletons. Idempotent per row by
     * council registration number (its natural key): a row whose registration
     * already maps to a provider is reported as SKIPPED_DUPLICATE, never
     * duplicated. Each created skeleton yields a single-use claim token,
     * returned ONCE in the response and stored only as a hash.
     */
    @Transactional
    public BulkPreloadResponse bulkPreload(BulkPreloadRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        UUID batchId = UUID.randomUUID();
        List<BulkPreloadResponse.PreloadResult> results = new ArrayList<>();
        int created = 0, skipped = 0, failed = 0;

        List<BulkPreloadRequest.PreloadRow> rows = request.rows();
        for (int i = 0; i < rows.size(); i++) {
            BulkPreloadRequest.PreloadRow row = rows.get(i);
            try {
                if (row.givenName() == null || row.givenName().isBlank()
                        || row.familyName() == null || row.familyName().isBlank()) {
                    failed++;
                    results.add(new BulkPreloadResponse.PreloadResult(
                            i, "FAILED", null, null, "givenName and familyName are required"));
                    continue;
                }

                Optional<ProviderEntity> existing = findByRegistration(ctx.tenantId(), row);
                if (existing.isPresent()) {
                    skipped++;
                    results.add(new BulkPreloadResponse.PreloadResult(
                            i, "SKIPPED_DUPLICATE", existing.get().getProviderPublicId(), null,
                            "registration already mapped to a provider"));
                    continue;
                }

                ProviderEntity provider = createPreloadedSkeleton(ctx, batchId, row);
                String rawToken = issueClaimToken(ctx, provider, batchId, row.contactHint());

                publishEvent("PROVIDER", provider.getProviderPublicId(),
                        "varapi.provider.preloaded",
                        String.format("{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\","
                                        + "\"batchId\":\"%s\",\"lifecycleStatus\":\"PRELOADED\"}",
                                provider.getProviderPublicId(), provider.getImpiloHealthId(), batchId));

                created++;
                results.add(new BulkPreloadResponse.PreloadResult(
                        i, "CREATED", provider.getProviderPublicId(), rawToken, "preloaded"));
            } catch (RuntimeException ex) {
                failed++;
                log.warn("preload row {} failed: {}", i, ex.getMessage());
                results.add(new BulkPreloadResponse.PreloadResult(
                        i, "FAILED", null, null, ex.getMessage()));
            }
        }

        log.info("bulk preload batch={} created={} skipped={} failed={}", batchId, created, skipped, failed);
        return new BulkPreloadResponse(batchId, created, skipped, failed, results);
    }

    private Optional<ProviderEntity> findByRegistration(UUID tenantId, BulkPreloadRequest.PreloadRow row) {
        if (row.registrationNumber() == null || row.registrationNumber().isBlank()) {
            return Optional.empty();
        }
        Optional<ProviderCouncilRegistrationRecordEntity> record =
                (row.councilCode() != null && !row.councilCode().isBlank())
                        ? registrationRepository
                            .findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
                                tenantId, row.councilCode().trim(), row.registrationNumber().trim())
                        : registrationRepository.findFirstByTenantIdAndRegistrationNumberIgnoreCase(
                                tenantId, row.registrationNumber().trim());
        return record.map(ProviderCouncilRegistrationRecordEntity::getProvider);
    }

    private ProviderEntity createPreloadedSkeleton(TrustContext ctx, UUID batchId,
                                                   BulkPreloadRequest.PreloadRow row) {
        ProviderEntity provider = new ProviderEntity();
        provider.setTenantId(ctx.tenantId());
        // A preloaded skeleton has no real person anchor yet — allocate a
        // provisional one that is rebound to the claimant's verified Health ID
        // at claim time. Until claimed the profile is INACTIVE.
        provider.setImpiloHealthId(UUID.randomUUID());
        provider.setProviderPublicId(generateProviderPublicId());
        provider.setGivenName(row.givenName().trim());
        provider.setFamilyName(row.familyName().trim());
        provider.setProfession(row.profession());
        provider.setCadre(row.cadre());
        provider.setEmploymentOrgId(row.employmentOrgId());
        provider.setStatus(STATUS_PRELOADED);
        provider.setLifecycleStatus(LIFECYCLE_PRELOADED);
        provider.setActiveFlag(false);
        provider.setBootstrapOrigin(ORIGIN_BULK_PRELOAD);
        provider.setPreloadBatchId(batchId);
        provider.setVersion(1);
        provider.setCreatedBy(ctx.actorId());
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        linkCouncilRegistration(ctx, provider, row);
        return provider;
    }

    /**
     * Record the council registration so the existing council/EC resolver can
     * find this preloaded profile (and so future preloads de-duplicate on it).
     */
    private void linkCouncilRegistration(TrustContext ctx, ProviderEntity provider,
                                         BulkPreloadRequest.PreloadRow row) {
        if (row.registrationNumber() == null || row.registrationNumber().isBlank()
                || row.councilCode() == null || row.councilCode().isBlank()) {
            return;
        }
        Optional<CouncilEntity> council = councilRepository.findByCouncilCode(row.councilCode().trim());
        if (council.isEmpty()) {
            log.warn("preload: unknown council code {} — registration not linked", row.councilCode());
            return;
        }
        ProviderCouncilRegistrationRecordEntity record = new ProviderCouncilRegistrationRecordEntity();
        record.setTenantId(ctx.tenantId());
        record.setProvider(provider);
        record.setCouncil(council.get());
        record.setRegistrationNumber(row.registrationNumber().trim());
        record.setStatus("PROVISIONAL");
        registrationRepository.save(record);
    }

    // ── Self-claim ──────────────────────────────────────────────────────────

    /**
     * Read-only preview of what a claim token would claim, for the claimant to
     * confirm before committing. Returns empty for any non-redeemable token
     * (never leaks why) so a token cannot be probed.
     */
    @Transactional(readOnly = true)
    public Optional<ClaimProfileResponse> previewClaim(String rawToken) {
        TrustContext ctx = TrustContextHolder.require();
        return loadRedeemableToken(ctx.tenantId(), rawToken)
                .flatMap(token -> providerRepository.findByIdAndTenantId(token.getProviderId(), ctx.tenantId()))
                .map(this::toClaimResponse);
    }

    /**
     * Claim a preloaded profile: validate the token, bind the claimant's Health
     * ID to the skeleton, and transition PRELOADED → CLAIMED (ACTIVE). Single
     * use — the token is burned on success.
     */
    @Transactional
    public ClaimProfileResponse claimProfile(String rawToken, UUID claimantHealthId) {
        TrustContext ctx = TrustContextHolder.require();
        if (claimantHealthId == null) {
            throw new IllegalArgumentException("claimantHealthId is required");
        }

        ProviderClaimTokenEntity token = loadRedeemableToken(ctx.tenantId(), rawToken)
                .orElseThrow(() -> new IllegalStateException("Claim token is invalid, expired, or already used"));

        Instant now = Instant.now();
        // Atomically burn the token (ISSUED → CLAIMED) BEFORE mutating the
        // provider. The conditional UPDATE's row lock serialises concurrent
        // /claim calls on the same token: exactly one gets rowcount == 1, every
        // other concurrent redemption sees 0 and is rejected here. This is the
        // single-use guard that closes the account-takeover race. A later guard
        // throw (e.g. one-person-one-profile) rolls this UPDATE back because the
        // whole method is @Transactional.
        int redeemed = claimTokenRepository.redeem(token.getId(), now, claimantHealthId);
        if (redeemed != 1) {
            throw new IllegalStateException("Claim token is invalid, expired, or already used");
        }

        ProviderEntity provider = providerRepository.findByIdAndTenantId(token.getProviderId(), ctx.tenantId())
                .orElseThrow(() -> new IllegalStateException("Preloaded provider no longer exists"));

        if (!LIFECYCLE_PRELOADED.equals(provider.getLifecycleStatus())) {
            throw new IllegalStateException("Provider profile is not in a claimable state");
        }

        // Guard against claiming a profile onto a Health ID that already owns a
        // distinct provider record (one person, one provider profile).
        final Long preloadedProviderId = provider.getId();
        providerRepository.findByTenantIdAndImpiloHealthId(ctx.tenantId(), claimantHealthId)
                .filter(p -> !p.getId().equals(preloadedProviderId))
                .ifPresent(p -> {
                    throw new IllegalStateException("This person already has a provider profile");
                });

        provider.setImpiloHealthId(claimantHealthId);
        provider.setClaimedHealthId(claimantHealthId);
        provider.setClaimedAt(now);
        provider.setLifecycleStatus(LIFECYCLE_CLAIMED);
        provider.setStatus(STATUS_ACTIVE);
        provider.setActiveFlag(true);
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        // The token was already burned atomically above (claimTokenRepository.redeem);
        // no second, non-atomic save here — that would re-open the race window.

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                "varapi.provider.claimed",
                String.format("{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\","
                                + "\"lifecycleStatus\":\"CLAIMED\"}",
                        provider.getProviderPublicId(), provider.getImpiloHealthId()));

        log.info("provider profile claimed: providerPublicId={}", provider.getProviderPublicId());
        return toClaimResponse(provider);
    }

    private Optional<ProviderClaimTokenEntity> loadRedeemableToken(UUID tenantId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return claimTokenRepository.findByTenantIdAndTokenHash(tenantId, hash(rawToken.trim()))
                .filter(t -> t.isRedeemable(Instant.now()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String issueClaimToken(TrustContext ctx, ProviderEntity provider, UUID batchId, String contactHint) {
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setTenantId(ctx.tenantId());
        token.setProviderId(provider.getId());
        token.setTokenHash(hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setIssuedToHint(contactHint);
        token.setExpiresAt(Instant.now().plus(CLAIM_TOKEN_TTL_DAYS, ChronoUnit.DAYS));
        token.setPreloadBatchId(batchId);
        token.setCreatedBy(ctx.actorId());
        claimTokenRepository.save(token);
        return rawToken;
    }

    /** SHA-256 hex of a token — only the hash is ever persisted. */
    static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String generateProviderPublicId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }

    private ClaimProfileResponse toClaimResponse(ProviderEntity p) {
        return new ClaimProfileResponse(
                p.getProviderPublicId(),
                p.getImpiloHealthId(),
                p.getLifecycleStatus(),
                p.getGivenName(),
                p.getFamilyName(),
                p.getProfession());
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
