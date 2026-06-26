package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.BulkPreloadRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.BulkPreloadResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ClaimProfileResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderClaimTokenEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderClaimTokenRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the provider bootstrap chain (L3 W6): bulk preload of
 * PRELOADED skeletons with single-use claim tokens, and provider self-claim.
 *
 * <p>Pure-Mockito (no Spring context) — matches the existing
 * {@link CouncilRegistrationResolverServiceTest} / {@code ProviderApplicationFeeWorkflowTest}
 * conventions: mocked repositories, TrustContextHolder seeded per test.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProviderBootstrapServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderClaimTokenRepository claimTokenRepository;
    @Mock private ProviderCouncilRegistrationRecordRepository registrationRepository;
    @Mock private CouncilRepository councilRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ProviderBootstrapService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderBootstrapService(
                providerRepository,
                claimTokenRepository,
                registrationRepository,
                councilRepository,
                outboxRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "national-admin-1", "REGISTRY_ADMIN", "REGISTRY_ADMIN", "device",
                correlationId, null, null, null, AccessMode.INTERNAL));
        lenient().when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Make providerRepository.save assign an incrementing id and echo the entity. */
    private void stubProviderSaveWithIds() {
        AtomicLong seq = new AtomicLong(100);
        when(providerRepository.save(any(ProviderEntity.class))).thenAnswer(inv -> {
            ProviderEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(seq.getAndIncrement());
            }
            return p;
        });
    }

    private BulkPreloadRequest.PreloadRow row(String given, String family,
                                              String councilCode, String regNo) {
        return new BulkPreloadRequest.PreloadRow(
                given, family, "DOCTOR", "MEDICAL_OFFICER",
                councilCode, regNo, "j***@example.com", 7L);
    }

    // ── (1) Bulk preload creates skeletons + issues one-time tokens ────────────

    @Test
    void bulkPreload_createsPreloadedSkeleton_andIssuesOneTimeToken() {
        stubProviderSaveWithIds();
        when(claimTokenRepository.save(any(ProviderClaimTokenEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // No existing registration → not a duplicate.
        when(registrationRepository
                .findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
                        eq(tenantId), any(), any()))
                .thenReturn(Optional.empty());

        BulkPreloadResponse resp = service.bulkPreload(new BulkPreloadRequest(
                List.of(row("Tariro", "Moyo", "MDPCZ", "EC-1001"))));

        assertThat(resp.created()).isEqualTo(1);
        assertThat(resp.skipped()).isZero();
        assertThat(resp.failed()).isZero();
        assertThat(resp.batchId()).isNotNull();
        assertThat(resp.results()).hasSize(1);

        BulkPreloadResponse.PreloadResult result = resp.results().get(0);
        assertThat(result.outcome()).isEqualTo("CREATED");
        assertThat(result.providerPublicId()).isNotBlank();
        // The raw claim token is returned ONCE, in the response only.
        assertThat(result.claimToken()).isNotBlank();

        // The persisted skeleton is PRELOADED / INACTIVE, not operational.
        ArgumentCaptor<ProviderEntity> providerCaptor = ArgumentCaptor.forClass(ProviderEntity.class);
        verify(providerRepository, atLeastOnce()).save(providerCaptor.capture());
        ProviderEntity saved = providerCaptor.getValue();
        assertThat(saved.getLifecycleStatus()).isEqualTo("PRELOADED");
        assertThat(saved.getStatus()).isEqualTo("INACTIVE");
        assertThat(saved.getActiveFlag()).isFalse();
        assertThat(saved.getBootstrapOrigin()).isEqualTo("BULK_PRELOAD");
        assertThat(saved.getPreloadBatchId()).isEqualTo(resp.batchId());

        // Only the SHA-256 hash of the token is persisted — never the raw token.
        ArgumentCaptor<ProviderClaimTokenEntity> tokenCaptor =
                ArgumentCaptor.forClass(ProviderClaimTokenEntity.class);
        verify(claimTokenRepository).save(tokenCaptor.capture());
        ProviderClaimTokenEntity persistedToken = tokenCaptor.getValue();
        assertThat(persistedToken.getStatus()).isEqualTo(ProviderClaimTokenEntity.STATUS_ISSUED);
        assertThat(persistedToken.getTokenHash())
                .isEqualTo(ProviderBootstrapService.hash(result.claimToken()));
        assertThat(persistedToken.getTokenHash()).isNotEqualTo(result.claimToken());

        // A preload event is emitted to the outbox.
        verify(outboxRepository, atLeastOnce()).save(any());
    }

    // ── (2) Idempotent per council registration (SKIPPED_DUPLICATE) ────────────

    @Test
    void bulkPreload_isIdempotentPerCouncilRegistration_skipsDuplicate() {
        ProviderEntity existing = new ProviderEntity();
        existing.setId(55L);
        existing.setProviderPublicId("EXISTING-PUB");
        ProviderCouncilRegistrationRecordEntity record = new ProviderCouncilRegistrationRecordEntity();
        record.setProvider(existing);
        when(registrationRepository
                .findFirstByTenantIdAndCouncil_CouncilCodeIgnoreCaseAndRegistrationNumberIgnoreCase(
                        eq(tenantId), eq("MDPCZ"), eq("EC-1001")))
                .thenReturn(Optional.of(record));

        BulkPreloadResponse resp = service.bulkPreload(new BulkPreloadRequest(
                List.of(row("Tariro", "Moyo", "MDPCZ", "EC-1001"))));

        assertThat(resp.created()).isZero();
        assertThat(resp.skipped()).isEqualTo(1);
        assertThat(resp.failed()).isZero();
        BulkPreloadResponse.PreloadResult result = resp.results().get(0);
        assertThat(result.outcome()).isEqualTo("SKIPPED_DUPLICATE");
        assertThat(result.providerPublicId()).isEqualTo("EXISTING-PUB");
        assertThat(result.claimToken()).isNull();

        // No new skeleton, no new token for a duplicate row.
        verify(providerRepository, never()).save(any());
        verify(claimTokenRepository, never()).save(any());
    }

    // ── (3) Self-claim binds Health ID, transitions PRELOADED→CLAIMED, burns token

    @Test
    void claimProfile_bindsHealthId_transitionsToClaimed_andBurnsToken() {
        String rawToken = "raw-claim-token-abc";
        UUID claimantHealthId = UUID.randomUUID();

        ProviderEntity preloaded = new ProviderEntity();
        preloaded.setId(200L);
        preloaded.setTenantId(tenantId);
        preloaded.setProviderPublicId("PUB-200");
        preloaded.setLifecycleStatus("PRELOADED");
        preloaded.setStatus("INACTIVE");
        preloaded.setActiveFlag(false);
        preloaded.setGivenName("Tariro");
        preloaded.setFamilyName("Moyo");
        preloaded.setProfession("DOCTOR");

        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setId(1L);
        token.setTenantId(tenantId);
        token.setProviderId(200L);
        token.setTokenHash(ProviderBootstrapService.hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(providerRepository.findByIdAndTenantId(200L, tenantId))
                .thenReturn(Optional.of(preloaded));
        // Claimant does not already own a different provider profile.
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, claimantHealthId))
                .thenReturn(Optional.empty());
        when(providerRepository.save(any(ProviderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // Atomic conditional redemption wins the race: rowcount == 1.
        when(claimTokenRepository.redeem(eq(1L), any(Instant.class), eq(claimantHealthId)))
                .thenReturn(1);

        ClaimProfileResponse resp = service.claimProfile(rawToken, claimantHealthId);

        assertThat(resp.lifecycleStatus()).isEqualTo("CLAIMED");
        assertThat(resp.impiloHealthId()).isEqualTo(claimantHealthId);
        assertThat(resp.providerPublicId()).isEqualTo("PUB-200");

        // Skeleton is now operational and bound to the claimant.
        assertThat(preloaded.getLifecycleStatus()).isEqualTo("CLAIMED");
        assertThat(preloaded.getStatus()).isEqualTo("ACTIVE");
        assertThat(preloaded.getActiveFlag()).isTrue();
        assertThat(preloaded.getImpiloHealthId()).isEqualTo(claimantHealthId);
        assertThat(preloaded.getClaimedHealthId()).isEqualTo(claimantHealthId);

        // The token is burned (single-use) via the ATOMIC conditional UPDATE,
        // not a read-modify-write save: redeem(...) is the only burn path.
        verify(claimTokenRepository).redeem(eq(1L), any(Instant.class), eq(claimantHealthId));
        verify(claimTokenRepository, never()).save(any());
    }

    // ── (3b) Concurrent redemption: the loser's atomic redeem rowcount==0 → reject
    @Test
    void claimProfile_atomicRedeemReturnsZero_rejectsConcurrentClaim() {
        String rawToken = "raw-claim-token-race";
        UUID claimantHealthId = UUID.randomUUID();

        // The token still LOOKS redeemable to the in-memory pre-check (a concurrent
        // claim has not yet committed when this thread reads it)...
        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setId(7L);
        token.setTenantId(tenantId);
        token.setProviderId(700L);
        token.setTokenHash(ProviderBootstrapService.hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        // ...but the atomic conditional UPDATE loses the race: another claim already
        // flipped ISSUED → CLAIMED, so this redeem matches no row (rowcount == 0).
        when(claimTokenRepository.redeem(eq(7L), any(Instant.class), eq(claimantHealthId)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.claimProfile(rawToken, claimantHealthId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid, expired, or already used");

        // Loser never touches the provider: no read, no mutation, no event.
        verify(providerRepository, never()).findByIdAndTenantId(any(), any());
        verify(providerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void claimProfile_secondClaimFails_tokenIsSingleUse() {
        String rawToken = "raw-claim-token-xyz";
        UUID claimantHealthId = UUID.randomUUID();

        // Token already CLAIMED → not redeemable on the second attempt.
        ProviderClaimTokenEntity burned = new ProviderClaimTokenEntity();
        burned.setTenantId(tenantId);
        burned.setProviderId(201L);
        burned.setTokenHash(ProviderBootstrapService.hash(rawToken));
        burned.setStatus(ProviderClaimTokenEntity.STATUS_CLAIMED);
        burned.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(burned));

        assertThatThrownBy(() -> service.claimProfile(rawToken, claimantHealthId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid, expired, or already used");

        // No state mutation when the claim is rejected.
        verify(providerRepository, never()).save(any());
    }

    // ── (4) Claim rejected when person already has a provider profile ──────────

    @Test
    void claimProfile_rejectedWhenPersonAlreadyHasProviderProfile() {
        String rawToken = "raw-claim-token-dup";
        UUID claimantHealthId = UUID.randomUUID();

        ProviderEntity preloaded = new ProviderEntity();
        preloaded.setId(300L);
        preloaded.setTenantId(tenantId);
        preloaded.setLifecycleStatus("PRELOADED");

        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setTenantId(tenantId);
        token.setProviderId(300L);
        token.setTokenHash(ProviderBootstrapService.hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        // The claimant already owns a *different* provider record.
        ProviderEntity otherProfile = new ProviderEntity();
        otherProfile.setId(999L);

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        // The atomic redeem succeeds first (this claim won the row), but the
        // one-person-one-profile guard fires AFTER it.
        when(claimTokenRepository.redeem(any(), any(Instant.class), eq(claimantHealthId)))
                .thenReturn(1);
        when(providerRepository.findByIdAndTenantId(300L, tenantId))
                .thenReturn(Optional.of(preloaded));
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, claimantHealthId))
                .thenReturn(Optional.of(otherProfile));

        assertThatThrownBy(() -> service.claimProfile(rawToken, claimantHealthId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a provider profile");

        // The skeleton is NOT mutated on rejection. The redeem UPDATE is rolled
        // back by the surrounding @Transactional boundary (the guard throws), so
        // the token returns to ISSUED at the DB layer — there is no second,
        // non-atomic save that could leave it half-burned.
        assertThat(preloaded.getLifecycleStatus()).isEqualTo("PRELOADED");
        verify(providerRepository, never()).save(any());
        verify(claimTokenRepository, never()).save(any());
    }

    // ── (5) previewClaim returns empty for invalid/expired token (no leak) ─────

    @Test
    void previewClaim_returnsEmptyForUnknownToken_noLeak() {
        when(claimTokenRepository.findByTenantIdAndTokenHash(eq(tenantId), any()))
                .thenReturn(Optional.empty());

        Optional<ClaimProfileResponse> preview = service.previewClaim("bogus-token");

        assertThat(preview).isEmpty();
        // Never touches the provider table for a non-redeemable token.
        verify(providerRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void previewClaim_returnsEmptyForExpiredToken_noLeak() {
        String rawToken = "expired-token";
        ProviderClaimTokenEntity expired = new ProviderClaimTokenEntity();
        expired.setTenantId(tenantId);
        expired.setProviderId(400L);
        expired.setTokenHash(ProviderBootstrapService.hash(rawToken));
        expired.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS)); // past expiry

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(expired));

        Optional<ClaimProfileResponse> preview = service.previewClaim(rawToken);

        assertThat(preview).isEmpty();
        verify(providerRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void previewClaim_returnsProfileForRedeemableToken() {
        String rawToken = "good-token";
        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setTenantId(tenantId);
        token.setProviderId(500L);
        token.setTokenHash(ProviderBootstrapService.hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        ProviderEntity preloaded = new ProviderEntity();
        preloaded.setId(500L);
        preloaded.setProviderPublicId("PUB-500");
        preloaded.setLifecycleStatus("PRELOADED");
        preloaded.setGivenName("Rudo");
        preloaded.setFamilyName("Ncube");
        preloaded.setProfession("NURSE");

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(providerRepository.findByIdAndTenantId(500L, tenantId))
                .thenReturn(Optional.of(preloaded));

        Optional<ClaimProfileResponse> preview = service.previewClaim(rawToken);

        assertThat(preview).isPresent();
        assertThat(preview.get().providerPublicId()).isEqualTo("PUB-500");
        assertThat(preview.get().lifecycleStatus()).isEqualTo("PRELOADED");
        assertThat(preview.get().familyName()).isEqualTo("Ncube");
        // Read-only preview: nothing is persisted.
        verify(providerRepository, never()).save(any());
        verify(claimTokenRepository, never()).save(any());
    }
}
