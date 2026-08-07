package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

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
 * Enforced in-service: PROVIDER-PRELOAD-ADMIN (bulk preload requires
 * national-admin/org-rep/system capacity) and PROVIDER-SELF-CLAIM (claim
 * requires claimantHealthId == authenticated actor; SYSTEM excepted).</p>
 */
@Service
public class ProviderBootstrapService {

    /**
     * Bulk-preloading provider records. Was
     * {@code {SYSTEM, NATIONAL_ADMIN, ORG_REPRESENTATIVE, OPERATOR, REGISTRY_ADMIN}} — the only one
     * of the five drifted varapi sets that already carried OPERATOR, so this is the one that worked.
     */
    private static final ActorTypeGuard.Duty BULK_PRELOAD_PROVIDERS = new ActorTypeGuard.Duty(
            "bulk-preloading provider records",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            java.util.Set.of("NATIONAL_ADMIN", "ORG_REPRESENTATIVE", "REGISTRY_ADMIN"));

    /**
     * The assisted-desk override on claiming a provider profile: who may claim a profile that is
     * not their own. Everyone else must be the claimant themselves. Same shape and same widening as
     * {@code ProviderRecoveryService.ASSISTED_DESK_RECOVERY}.
     */
    private static final ActorTypeGuard.Duty ASSISTED_DESK_CLAIM = new ActorTypeGuard.Duty(
            "claiming a provider profile on someone else's behalf",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            java.util.Set.of("REGISTRY_ADMIN"));

    private static final Logger log = LoggerFactory.getLogger(ProviderBootstrapService.class);

    private static final String ORIGIN_BULK_PRELOAD = "BULK_PRELOAD";
    // Typed lifecycle constants (string values identical to the historical untyped
    // literals — no stored data changes).
    private static final String LIFECYCLE_PRELOADED =
            zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus.PRELOADED.name();
    private static final String LIFECYCLE_CLAIMED =
            zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus.CLAIMED.name();
    private static final long CLAIM_TOKEN_TTL_DAYS = 30;

    private final ProviderRepository providerRepository;
    private final ProviderClaimTokenRepository claimTokenRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final CouncilRepository councilRepository;
    private final EventOutboxRepository outboxRepository;
    private final ProviderClaimAdjudicationService providerClaimAdjudicationService;
    private final ProviderAuthorizationLinkService authorizationLinkService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProviderBootstrapService(ProviderRepository providerRepository,
                                    ProviderClaimTokenRepository claimTokenRepository,
                                    ProviderCouncilRegistrationRecordRepository registrationRepository,
                                    CouncilRepository councilRepository,
                                    EventOutboxRepository outboxRepository,
                                    ProviderClaimAdjudicationService providerClaimAdjudicationService,
                                    ProviderAuthorizationLinkService authorizationLinkService) {
        this.providerRepository = providerRepository;
        this.claimTokenRepository = claimTokenRepository;
        this.registrationRepository = registrationRepository;
        this.councilRepository = councilRepository;
        this.outboxRepository = outboxRepository;
        this.providerClaimAdjudicationService = providerClaimAdjudicationService;
        this.authorizationLinkService = authorizationLinkService;
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
        // Policy PROVIDER-PRELOAD-ADMIN: only national-admin / organisation-rep
        // capacities may bulk-preload provider skeletons (defense-in-depth
        // behind the ext_authz gate).
        String actorType = ctx.actorType() != null ? ctx.actorType().trim().toUpperCase(java.util.Locale.ROOT) : "";
        ActorTypeGuard.require(actorType, ctx.actorId(), BULK_PRELOAD_PROVIDERS);
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
        // Canonical axis first; status / active_flag / licence_status are derived
        // (PRELOADED projects to status=INACTIVE, active=false).
        provider.setLifecycleStatus(LIFECYCLE_PRELOADED);
        provider.deriveStatusProjections();
        provider.setBootstrapOrigin(ORIGIN_BULK_PRELOAD);
        // IATG Wave-1 axes: a fresh skeleton is honestly self-asserted and unverified.
        provider.setTrustLevel(zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel.SELF_ASSERTED.name());
        provider.setRegistryStatus(zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus.PENDING_VERIFICATION.name());
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
        return claimProfile(rawToken, claimantHealthId, null);
    }

    /**
     * Claim a preloaded profile, recording the assurance outcome the trusted
     * BFF proved (from the Wave-G person-proofing spine) onto the authorization
     * link. A null outcome defaults to RECORD_LINKED (the token-possession +
     * person-first-login baseline).
     */
    @Transactional
    public ClaimProfileResponse claimProfile(String rawToken, UUID claimantHealthId, String assuranceOutcome) {
        TrustContext ctx = TrustContextHolder.require();
        if (claimantHealthId == null) {
            throw new IllegalArgumentException("claimantHealthId is required");
        }
        // Policy PROVIDER-SELF-CLAIM: a profile may only be claimed BY the
        // person it anchors — the claimant health id must be the authenticated
        // actor. SYSTEM (governed migrations) and REGISTRY_ADMIN (assisted
        // claims at a registry desk) are excepted; the single-use token remains
        // the possession factor in those flows.
        String claimActorType = ctx.actorType() != null ? ctx.actorType().trim().toUpperCase(java.util.Locale.ROOT) : "";
        if (!ActorTypeGuard.permits(claimActorType, ASSISTED_DESK_CLAIM)
                && (ctx.actorId() == null || !claimantHealthId.toString().equalsIgnoreCase(ctx.actorId().trim()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "A provider profile may only be claimed by the authenticated person it belongs to");
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

        return toClaimResponse(
                completeClaim(provider, claimantHealthId, "claim-token:" + token.getId(), assuranceOutcome));
    }

    /**
     * Bind a claimable provider profile to a person and open participation.
     *
     * <p>This is the whole of what "claiming" does, extracted so that every route into it produces
     * the same effect and the same record. Two routes exist and a third is coming:</p>
     *
     * <ul>
     *   <li>a claim <b>token</b>, redeemed by the practitioner ({@link #claimProfile});</li>
     *   <li>a <b>reviewer's approval</b> of a council-number claim request
     *       ({@link #completeClaimByReview}) — the route the 4,241 HPA-listed practitioners need,
     *       because the import carried no contact channel and so no token could ever be issued
     *       to them.</li>
     * </ul>
     *
     * <p>What differs between the routes is only the <em>authorisation</em> to claim — a redeemed
     * token, or a named reviewer's decision — and that difference is recorded in
     * {@code bindingSource}. What must not differ is the effect, which is why this is one method
     * and not two: {@code claimed_at} is what booking reads, so a route that bound the person but
     * forgot to set it would produce a provider who is claimed and still unbookable.</p>
     *
     * @param provider          the profile being claimed; must be in a claimable lifecycle state
     * @param claimantHealthId  the person anchor the profile binds to
     * @param bindingSource     what authorised this claim, recorded on the authorisation link
     * @param assuranceOutcome  identity-assurance outcome to record, or null for RECORD_LINKED
     */
    @Transactional
    public ProviderEntity completeClaim(ProviderEntity provider,
                                        UUID claimantHealthId,
                                        String bindingSource,
                                        String assuranceOutcome) {
        TrustContext ctx = TrustContextHolder.require();
        Instant now = Instant.now();

        if (!LIFECYCLE_PRELOADED.equals(provider.getLifecycleStatus())) {
            throw new IllegalStateException("Provider profile is not in a claimable state");
        }

        // Guard against claiming a profile onto a Health ID that already owns a
        // distinct provider record (one person, one provider profile).
        final Long preloadedProviderId = provider.getId();
        providerRepository.findByTenantIdAndImpiloHealthId(ctx.tenantId(), claimantHealthId)
                .filter(p -> !p.getId().equals(preloadedProviderId))
                .ifPresent(p -> {
                    // WS-F: when adjudication is enabled, escalate the conflict (mark the existing
                    // profile CONFLICT + start a provider adjudication) in its own committed
                    // transaction, then still reject the claim. Default-off: behaviour is unchanged.
                    providerClaimAdjudicationService.escalate(
                            ctx.tenantId(), p.getId(), claimantHealthId, ctx.actorId(),
                            ctx.correlationId() != null ? ctx.correlationId().toString() : null);
                    throw new IllegalStateException("This person already has a provider profile"
                            + (providerClaimAdjudicationService.isEnabled()
                                    ? " — the conflict has been routed to adjudication" : ""));
                });

        provider.setImpiloHealthId(claimantHealthId);
        provider.setClaimedHealthId(claimantHealthId);
        provider.setClaimedAt(now);
        // Canonical axis first; status / active_flag / licence_status are derived
        // (CLAIMED projects to status=ACTIVE, active=true).
        provider.setLifecycleStatus(LIFECYCLE_CLAIMED);
        provider.deriveStatusProjections();
        // Channel typing at claim, derived from preload/bootstrap provenance
        // (BULK_PRELOAD -> WORKFORCE_B, COUNCIL_IMPORT -> REGULATORY_A,
        // SELF_REGISTERED -> SELF). Never overwrites an already-set channel.
        if (provider.getOnboardingChannel() == null) {
            zw.gov.mohcc.impilo.varapi.enums.OnboardingChannel channel =
                    zw.gov.mohcc.impilo.varapi.enums.OnboardingChannel.fromBootstrapOrigin(provider.getBootstrapOrigin());
            if (channel != null) {
                provider.setOnboardingChannel(channel.name());
            }
        }
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        // Authoritative binding record (D-P1) — same transaction as the claim,
        // so the claimed_health_id pointer and the link row commit together.
        authorizationLinkService.recordBinding(
                ctx.tenantId(), provider, claimantHealthId,
                zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAuthorizationLinkEntity.TYPE_CLAIM,
                bindingSource,
                assuranceOutcome != null && !assuranceOutcome.isBlank() ? assuranceOutcome.trim() : "RECORD_LINKED",
                provider.getOnboardingChannel() != null ? provider.getOnboardingChannel() : "BOOTSTRAP_CLAIM",
                ctx.actorId());

        // Where a token authorised this claim it was already burned atomically by the caller
        // (claimTokenRepository.redeem); no second, non-atomic save here — that would re-open
        // the race window.

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                "varapi.provider.claimed",
                String.format("{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\","
                                + "\"lifecycleStatus\":\"CLAIMED\",\"bindingSource\":\"%s\"}",
                        provider.getProviderPublicId(), provider.getImpiloHealthId(), bindingSource));

        log.info("provider profile claimed: providerPublicId={} via {}",
                provider.getProviderPublicId(), bindingSource);
        return provider;
    }

    /**
     * Complete a claim on a reviewer's authority rather than on a token's.
     *
     * <p>The 4,241 practitioners HPA listed hold no claim token and never will — the import had no
     * contact channel to deliver one to. Their route is to submit a council-number claim request,
     * which a reviewer verifies against the council register. That approval is the identity
     * decision, so it is what authorises the binding here.</p>
     *
     * <p>Until this existed the workflow stopped one step short: a reviewer could approve, the
     * request went to APPROVED, and the provider row was never touched — so the practitioner
     * remained unclaimed and unbookable while the console showed their request as granted. An
     * approval that changes nothing is worse than no approval, because it reads as done.</p>
     */
    // REQUIRES_NEW, and that is load-bearing rather than incidental. decide() is itself
    // transactional, so joining it would mean a failure here marks the reviewer's whole
    // transaction rollback-only — the decision would vanish along with the failed binding, and
    // the caller's attempt to record WHY it failed would fail too, at commit. In its own
    // transaction the binding can fail cleanly and leave the verdict, and the explanation of the
    // failure, both intact. Same reasoning as providerClaimAdjudicationService.escalate above.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaimByReview(String providerPublicId, UUID claimantHealthId, String requestPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository
                .findByProviderPublicIdAndTenantId(providerPublicId, ctx.tenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "Approved claim references provider " + providerPublicId + ", which no longer exists"));

        // The reviewer checked the claimant against the council register; that is the assurance
        // this binding rests on, and it is named rather than left as a bare RECORD_LINKED so the
        // link says who vouched and on what basis.
        completeClaim(provider, claimantHealthId, "access-request:" + requestPublicId, "REVIEWER_VERIFIED");
    }

    /**
     * Turn participation on or off for the authenticated person's own provider profile.
     *
     * <p>Registration is not participation. Being on the HPA register makes a practitioner
     * searchable, verifiable and findable; it does not mean they have agreed to receive
     * appointment and prescription requests through Impilo. {@code claimed_at} is that agreement,
     * and it is what booking gates on, so this is the switch that decides whether a practitioner
     * can be booked.</p>
     *
     * <p><b>Why this exists separately from the claim routes.</b> A profile that is already bound
     * to a person has nothing left to prove: the binding happened at registration or at claim.
     * But there was no way to set {@code claimed_at} except by claiming a PRELOADED skeleton, so
     * the 27 self-registered providers in the estate — bound to a person, lifecycle REGISTERED —
     * could never opt in, and were therefore permanently unbookable. Not a policy, just a missing
     * door.</p>
     *
     * <p><b>PRELOADED is deliberately excluded, and this is the security-load-bearing part.</b>
     * Every one of the 4,241 HPA-imported skeletons already carries an {@code impilo_health_id}
     * from the import, so resolving a profile by the caller's Health ID can land on an unclaimed
     * skeleton. Allowing self opt-in there would let a person switch on a profile whose identity
     * nobody has checked, bypassing both the claim token and the reviewer — the whole point of
     * the council-number workflow. An unclaimed profile must be claimed first.</p>
     *
     * <p>Opting out clears {@code claimed_at} only. It does not unbind or un-claim: the
     * authoritative binding record is the authorisation link, which carries its own
     * {@code boundAt}, so the claim and when it happened survive an opt-out and a later opt-in
     * does not rewrite history.</p>
     */
    @Transactional
    public ClaimProfileResponse setParticipation(boolean participating) {
        TrustContext ctx = TrustContextHolder.require();
        UUID actor;
        try {
            actor = UUID.fromString(ctx.actorId());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Participation can only be set by an authenticated person");
        }

        ProviderEntity provider = providerRepository
                .findByTenantIdAndImpiloHealthId(ctx.tenantId(), actor)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No provider profile is linked to this person"));

        if (LIFECYCLE_PRELOADED.equals(provider.getLifecycleStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "This profile has not been claimed yet — claim it before choosing to participate");
        }

        boolean alreadyParticipating = provider.getClaimedAt() != null;
        if (alreadyParticipating == participating) {
            return toClaimResponse(provider);          // idempotent; nothing to record
        }

        provider.setClaimedAt(participating ? Instant.now() : null);
        if (participating && provider.getClaimedHealthId() == null) {
            provider.setClaimedHealthId(actor);
        }
        provider.deriveStatusProjections();
        provider.setUpdatedBy(ctx.actorId());
        provider = providerRepository.save(provider);

        publishEvent("PROVIDER", provider.getProviderPublicId(),
                participating ? "varapi.provider.participation.opted_in"
                              : "varapi.provider.participation.opted_out",
                String.format("{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\",\"participating\":%s}",
                        provider.getProviderPublicId(), provider.getImpiloHealthId(), participating));

        log.info("provider participation set: providerPublicId={} participating={}",
                provider.getProviderPublicId(), participating);
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
