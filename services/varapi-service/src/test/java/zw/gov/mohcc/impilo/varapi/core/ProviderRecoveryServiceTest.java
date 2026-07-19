package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryCompleteResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryEvidence;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecoveryDtos.RecoveryInitiateResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderClaimTokenEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderClaimTokenRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for provider profile recovery (IATG WS-F): recover-not-reissue.
 *
 * <p>Pure-Mockito, matching {@link ProviderBootstrapServiceTest} conventions:
 * mocked repositories, TrustContextHolder seeded per test. The load-bearing
 * assertions: the SAME providerPublicId pre/post recovery, 404 for an unknown
 * Health ID, and no new provider record is EVER created on the recovery path.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProviderRecoveryServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderClaimTokenRepository claimTokenRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ProviderAuthorizationLinkService authorizationLinkService;

    private ProviderRecoveryService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();
    private final UUID personHealthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderRecoveryService(
                providerRepository, claimTokenRepository, outboxRepository, authorizationLinkService);
        seedActor(personHealthId.toString(), "CITIZEN");
        lenient().when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void seedActor(String actorId, String actorType) {
        TrustContextHolder.set(new TrustContext(
                tenantId, actorId, actorType, "SELF_SERVICE", "device",
                correlationId, null, null, null, AccessMode.INTERNAL));
    }

    private ProviderEntity claimedProvider(long id, String publicId, UUID healthId) {
        ProviderEntity p = new ProviderEntity();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setProviderPublicId(publicId);
        p.setImpiloHealthId(healthId);
        p.setLifecycleStatus("CLAIMED");
        p.setStatus("ACTIVE");
        p.setActiveFlag(true);
        p.setGivenName("Tariro");
        p.setFamilyName("Moyo");
        p.setProfession("DOCTOR");
        return p;
    }

    private RecoveryEvidence councilEvidence() {
        return new RecoveryEvidence("COUNCIL_NUMBER", "COUNCIL_RECORD", "EC***", 0.8);
    }

    // ── initiate ──────────────────────────────────────────────────────────────

    @Test
    void initiate_unknownHealthId_is404_andNeverCreatesProvider() {
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, personHealthId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(personHealthId, councilEvidence()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        // Recovery NEVER mints: no provider save, no token issued.
        verify(providerRepository, never()).save(any());
        verify(claimTokenRepository, never()).save(any());
    }

    @Test
    void initiate_issuesRecoveryPurposeToken_boundToExistingProvider() {
        ProviderEntity existing = claimedProvider(200L, "PUB-200", personHealthId);
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, personHealthId))
                .thenReturn(Optional.of(existing));
        when(claimTokenRepository.save(any(ProviderClaimTokenEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecoveryInitiateResponse resp = service.initiate(personHealthId, councilEvidence());

        assertThat(resp.providerPublicId()).isEqualTo("PUB-200");
        assertThat(resp.recoveryToken()).isNotBlank();
        assertThat(resp.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<ProviderClaimTokenEntity> captor =
                ArgumentCaptor.forClass(ProviderClaimTokenEntity.class);
        verify(claimTokenRepository).save(captor.capture());
        ProviderClaimTokenEntity token = captor.getValue();
        // Bound to the EXISTING provider row; purpose encoded in issued_to_hint
        // (no schema change); only the hash is persisted.
        assertThat(token.getProviderId()).isEqualTo(200L);
        assertThat(token.getIssuedToHint())
                .startsWith(ProviderRecoveryService.RECOVERY_HINT_PREFIX)
                .contains("COUNCIL_NUMBER");
        assertThat(token.getTokenHash()).isEqualTo(ProviderBootstrapService.hash(resp.recoveryToken()));
        assertThat(token.getTokenHash()).isNotEqualTo(resp.recoveryToken());
        assertThat(token.getStatus()).isEqualTo(ProviderClaimTokenEntity.STATUS_ISSUED);

        // No provider mutation at initiation; event emitted.
        verify(providerRepository, never()).save(any());
        verify(outboxRepository, atLeastOnce()).save(any());
    }

    @Test
    void initiate_forbiddenWhenActorIsNotTheHealthId() {
        seedActor(UUID.randomUUID().toString(), "CITIZEN");

        assertThatThrownBy(() -> service.initiate(personHealthId, councilEvidence()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(claimTokenRepository, never()).save(any());
    }

    @Test
    void initiate_requiresEvidenceType() {
        assertThatThrownBy(() -> service.initiate(personHealthId,
                new RecoveryEvidence(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence.type");
    }

    // ── complete ──────────────────────────────────────────────────────────────

    @Test
    void complete_relinksLogin_withSameProviderPublicIdPreAndPost() {
        String rawToken = "recovery-raw-token";
        ProviderEntity existing = claimedProvider(200L, "PUB-200", personHealthId);
        String publicIdBefore = existing.getProviderPublicId();

        ProviderClaimTokenEntity token = recoveryToken(9L, 200L, rawToken);
        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(claimTokenRepository.redeem(eq(9L), any(Instant.class), eq(personHealthId)))
                .thenReturn(1);
        when(providerRepository.findByIdAndTenantId(200L, tenantId))
                .thenReturn(Optional.of(existing));
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, personHealthId))
                .thenReturn(Optional.of(existing)); // same row — not a different profile
        when(providerRepository.save(any(ProviderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveryCompleteResponse resp = service.complete(rawToken);

        // Recover-not-reissue: the SAME public identifier pre/post.
        assertThat(resp.providerPublicId()).isEqualTo(publicIdBefore).isEqualTo("PUB-200");
        assertThat(resp.impiloHealthId()).isEqualTo(personHealthId);
        assertThat(resp.lifecycleStatus()).isEqualTo("CLAIMED");

        // The single mutation is on the EXISTING row (same id) — never a new record.
        ArgumentCaptor<ProviderEntity> captor = ArgumentCaptor.forClass(ProviderEntity.class);
        verify(providerRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(200L);
        assertThat(captor.getValue().getProviderPublicId()).isEqualTo(publicIdBefore);
        assertThat(captor.getValue().getImpiloHealthId()).isEqualTo(personHealthId);

        // Atomic single-use burn, no non-atomic token save.
        verify(claimTokenRepository).redeem(eq(9L), any(Instant.class), eq(personHealthId));
        verify(claimTokenRepository, never()).save(any());
    }

    @Test
    void complete_rejectsBootstrapClaimToken_purposeGuard() {
        String rawToken = "plain-claim-token";
        ProviderClaimTokenEntity claimToken = recoveryToken(3L, 200L, rawToken);
        claimToken.setIssuedToHint("j***@example.com"); // bootstrap contact hint, NOT recovery purpose

        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(claimToken));

        assertThatThrownBy(() -> service.complete(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid, expired, or already used");

        // Purpose guard rejects before any redeem or provider touch.
        verify(claimTokenRepository, never()).redeem(any(), any(), any());
        verify(providerRepository, never()).save(any());
    }

    @Test
    void complete_atomicRedeemLoss_rejects() {
        String rawToken = "raced-recovery-token";
        ProviderClaimTokenEntity token = recoveryToken(4L, 200L, rawToken);
        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(claimTokenRepository.redeem(eq(4L), any(Instant.class), eq(personHealthId)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.complete(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid, expired, or already used");
        verify(providerRepository, never()).save(any());
    }

    @Test
    void complete_rejectsWhenActorAlreadyOwnsDifferentProfile() {
        String rawToken = "recovery-token-dup";
        ProviderEntity target = claimedProvider(200L, "PUB-200", UUID.randomUUID());
        ProviderEntity other = claimedProvider(999L, "PUB-999", personHealthId);

        ProviderClaimTokenEntity token = recoveryToken(5L, 200L, rawToken);
        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(claimTokenRepository.redeem(eq(5L), any(Instant.class), eq(personHealthId)))
                .thenReturn(1);
        when(providerRepository.findByIdAndTenantId(200L, tenantId))
                .thenReturn(Optional.of(target));
        when(providerRepository.findByTenantIdAndImpiloHealthId(tenantId, personHealthId))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.complete(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a provider profile");
        verify(providerRepository, never()).save(any());
    }

    @Test
    void complete_rejectsPreloadedProfile_useClaimJourneyInstead() {
        String rawToken = "recovery-token-preloaded";
        ProviderEntity preloaded = claimedProvider(300L, "PUB-300", UUID.randomUUID());
        preloaded.setLifecycleStatus("PRELOADED");

        ProviderClaimTokenEntity token = recoveryToken(6L, 300L, rawToken);
        when(claimTokenRepository.findByTenantIdAndTokenHash(
                tenantId, ProviderBootstrapService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(claimTokenRepository.redeem(eq(6L), any(Instant.class), eq(personHealthId)))
                .thenReturn(1);
        when(providerRepository.findByIdAndTenantId(300L, tenantId))
                .thenReturn(Optional.of(preloaded));

        assertThatThrownBy(() -> service.complete(rawToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim journey");
        verify(providerRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProviderClaimTokenEntity recoveryToken(long id, long providerId, String rawToken) {
        ProviderClaimTokenEntity token = new ProviderClaimTokenEntity();
        token.setId(id);
        token.setTenantId(tenantId);
        token.setProviderId(providerId);
        token.setTokenHash(ProviderBootstrapService.hash(rawToken));
        token.setStatus(ProviderClaimTokenEntity.STATUS_ISSUED);
        token.setIssuedToHint(ProviderRecoveryService.RECOVERY_HINT_PREFIX + "COUNCIL_NUMBER");
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        return token;
    }
}
