package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.PrescriptionStatus;
import zw.gov.mohcc.impilo.oros.integration.TshepoKeysClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.*;
import zw.gov.mohcc.impilo.oros.persistence.repository.*;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PrescriptionService} (OF-B2): draft→sign→activate atomicity,
 * token minting with NO clinical content, single-active token rotation on amendment,
 * atomic claim + server-side repeats decrement, RC-7 superseded-version refusal,
 * exhaustion, compensation release, and claim idempotency.
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private PrescriptionVersionRepository versionRepository;
    @Mock private PrescriptionItemRepository itemRepository;
    @Mock private PrescriptionTokenRepository tokenRepository;
    @Mock private PrescriptionClaimRepository claimRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private TshepoKeysClient tshepoKeysClient;

    private PrescriptionService service;
    private final ObjectMapper objectMapper = OrosTestObjectMapper.create();

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String RX_ID = "01RXAAAAAAAAAAAAAAAAAAAAAA";
    private static final String V1_ID = "01VERSION1XXXXXXXXXXXXXXXX";

    @BeforeEach
    void setUp() {
        service = new PrescriptionService(prescriptionRepository, versionRepository, itemRepository,
                tokenRepository, claimRepository, outboxRepository, tshepoKeysClient, objectMapper, 72);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "dr-mapfumo", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    /** Fake signer: wraps the payload as header.base64url(payload).sig so resolveToken can parse it. */
    private void stubSigner() {
        when(tshepoKeysClient.signJws(eq(TENANT_ID), any())).thenAnswer(inv -> {
            String payload = inv.getArgument(1);
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            return new TshepoKeysClient.SignedJws("key-1", "EdDSA", "eyJhbGciOiJFZERTQSJ9." + encoded + ".sig");
        });
    }

    private PrescriptionService.ItemData item(String code, boolean controlled) {
        return new PrescriptionService.ItemData(code, null, code + "-name", "500mg", "PO", "BD",
                5, null, null, null, controlled, true);
    }

    private PrescriptionEntity activeRx(int remaining) {
        PrescriptionEntity rx = new PrescriptionEntity();
        rx.setPrescriptionId(RX_ID);
        rx.setTenantId(TENANT_ID);
        rx.setPatientCpid("CPID-1");
        rx.setPrescriberId("dr-mapfumo");
        rx.setStatus(PrescriptionStatus.SIGNED_ACTIVE);
        rx.setRepeatsCeiling(3);
        rx.setRepeatsRemaining(remaining);
        rx.setCurrentVersionId(V1_ID);
        return rx;
    }

    private PrescriptionTokenEntity activeToken(PrescriptionEntity rx, String tokenJws) {
        PrescriptionTokenEntity token = new PrescriptionTokenEntity();
        token.setTokenId(tokenIdOf(tokenJws));
        token.setPrescriptionId(rx.getPrescriptionId());
        token.setPrescriptionVersionId(rx.getCurrentVersionId());
        token.setTenantId(TENANT_ID);
        token.setStatus(PrescriptionTokenEntity.STATUS_ACTIVE);
        token.setTokenJws(tokenJws);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return token;
    }

    private String tokenJwsFor(String tokenId, String versionId) {
        String payload = "{\"token_id\":\"" + tokenId + "\",\"prescription_version_id\":\"" + versionId + "\"}";
        return "hdr." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    private String tokenIdOf(String jws) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(jws.split("\\.")[1]), StandardCharsets.UTF_8);
            return objectMapper.readTree(payload).path("token_id").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("Authoring + signing")
    class AuthoringSigning {

        @Test
        void createDerivesControlledFlagAndStartsDraft() {
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                PrescriptionEntity rx = service.create(new PrescriptionService.CreateData(
                        "CPID-1", null, null, null, null, null, 3,
                        LocalDate.now(), LocalDate.now().plusMonths(6), true, "Hypertension",
                        List.of(item("C09AA05", false), item("N02AA01", true))));

                assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.DRAFT);
                assertThat(rx.isControlled()).isTrue();
                assertThat(rx.getRepeatsRemaining()).isEqualTo(3);
                assertThat(rx.getPrescriberId()).isEqualTo("dr-mapfumo");
                verify(outboxRepository).save(argThat(e -> "PRESCRIPTION_CREATED".equals(e.getEventType())));
            }
        }

        @Test
        void createWithoutItemsRejected() {
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.create(new PrescriptionService.CreateData(
                        "CPID-1", null, null, null, null, null, 1, null, null, true, null, List.of())))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("at least one coded medication");
            }
        }

        @Test
        void signActivatesAtomicallyAndMintsTokenWithNoClinicalContent() {
            stubSigner();
            PrescriptionEntity rx = activeRx(3);
            rx.setStatus(PrescriptionStatus.DRAFT);
            rx.setCurrentVersionId(null);
            when(prescriptionRepository.findByTenantIdAndPrescriptionId(TENANT_ID, RX_ID))
                    .thenReturn(Optional.of(rx));
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.findByPrescriptionIdAndStatus(RX_ID, PrescriptionTokenEntity.STATUS_ACTIVE))
                    .thenReturn(Optional.empty());
            PrescriptionVersionEntity v1 = new PrescriptionVersionEntity();
            v1.setPrescriptionVersionId(V1_ID);
            v1.setPrescriptionId(RX_ID);
            v1.setVersion(1);
            v1.setContentHash("a".repeat(64));
            when(versionRepository.findFirstByPrescriptionIdOrderByVersionDesc(RX_ID))
                    .thenReturn(Optional.of(v1));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                PrescriptionEntity signed = service.signAndActivate(RX_ID);

                assertThat(signed.getStatus()).isEqualTo(PrescriptionStatus.SIGNED_ACTIVE);
                assertThat(signed.getCurrentVersionId()).isEqualTo(V1_ID);
                assertThat(v1.isSigned()).isTrue();
                assertThat(v1.getSignedBy()).isEqualTo("dr-mapfumo");

                // Token payload discipline (§13.2.2): opaque refs only — no patient, no items,
                // no prescriber, nothing clinically derivable.
                verify(tokenRepository).save(argThat(token -> {
                    try {
                        String payload = new String(Base64.getUrlDecoder()
                                .decode(token.getTokenJws().split("\\.")[1]), StandardCharsets.UTF_8);
                        JsonNode node = objectMapper.readTree(payload);
                        List<String> fields = new ArrayList<>();
                        node.fieldNames().forEachRemaining(fields::add);
                        return fields.stream().allMatch(f -> Set.of(
                                "token_id", "prescription_version_id", "issued_at", "expires_at").contains(f));
                    } catch (Exception e) {
                        return false;
                    }
                }));
                verify(outboxRepository).save(argThat(e -> "PRESCRIPTION_SIGNED".equals(e.getEventType())));
                verify(outboxRepository).save(argThat(e -> "PRESCRIPTION_ACTIVATED".equals(e.getEventType())));
            }
        }

        @Test
        void signingUnavailableFailsClosed() {
            when(tshepoKeysClient.signJws(any(), any()))
                    .thenThrow(new OrosDomainException("SIGNING_UNAVAILABLE", 503, "no key"));
            PrescriptionEntity rx = activeRx(3);
            rx.setStatus(PrescriptionStatus.DRAFT);
            when(prescriptionRepository.findByTenantIdAndPrescriptionId(TENANT_ID, RX_ID))
                    .thenReturn(Optional.of(rx));
            PrescriptionVersionEntity v1 = new PrescriptionVersionEntity();
            v1.setPrescriptionVersionId(V1_ID);
            v1.setVersion(1);
            when(versionRepository.findFirstByPrescriptionIdOrderByVersionDesc(RX_ID))
                    .thenReturn(Optional.of(v1));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.signAndActivate(RX_ID))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("no key");
                // Activation never happened — no signed-but-inactive state, no token.
                assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.DRAFT);
                verify(tokenRepository, never()).save(any());
            }
        }

        @Test
        void amendCreatesUnsignedNextVersionAndSignRotatesToken() {
            stubSigner();
            PrescriptionEntity rx = activeRx(3);
            when(prescriptionRepository.findByTenantIdAndPrescriptionId(TENANT_ID, RX_ID))
                    .thenReturn(Optional.of(rx));
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PrescriptionVersionEntity v1 = new PrescriptionVersionEntity();
            v1.setPrescriptionVersionId(V1_ID);
            v1.setPrescriptionId(RX_ID);
            v1.setVersion(1);
            v1.setSignatureJws("signed.v1.jws");
            when(versionRepository.findFirstByPrescriptionIdOrderByVersionDesc(RX_ID))
                    .thenReturn(Optional.of(v1));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                PrescriptionVersionEntity v2 = service.amend(RX_ID, List.of(item("C09AA05", false)),
                        "Dose reduced after review");
                assertThat(v2.getVersion()).isEqualTo(2);
                assertThat(v2.getSupersedesVersionId()).isEqualTo(V1_ID);
                assertThat(v2.isSigned()).isFalse();

                // Now sign the amendment: predecessor token revoked + successor minted atomically.
                PrescriptionTokenEntity oldToken = activeToken(rx, tokenJwsFor("01TOKENOLDXXXXXXXXXXXXXXXX", V1_ID));
                when(versionRepository.findFirstByPrescriptionIdOrderByVersionDesc(RX_ID))
                        .thenReturn(Optional.of(v2));
                when(tokenRepository.findByPrescriptionIdAndStatus(RX_ID, PrescriptionTokenEntity.STATUS_ACTIVE))
                        .thenReturn(Optional.of(oldToken));
                when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                service.signAndActivate(RX_ID);

                assertThat(oldToken.getStatus()).isEqualTo(PrescriptionTokenEntity.STATUS_REVOKED);
                assertThat(oldToken.getRevokeReason()).isEqualTo("SUPERSEDED");
                assertThat(rx.getCurrentVersionId()).isEqualTo(v2.getPrescriptionVersionId());
            }
        }
    }

    @Nested
    @DisplayName("Claim (§13.2.3)")
    class Claims {

        @Test
        void claimDecrementsServerSideCounterAndReturnsAuthoritativeLines() {
            PrescriptionEntity rx = activeRx(2);
            String jws = tokenJwsFor("01TOKENAAAAAAAAAAAAAAAAAAA", V1_ID);
            PrescriptionTokenEntity token = activeToken(rx, jws);
            when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
            when(prescriptionRepository.findForClaim(RX_ID)).thenReturn(Optional.of(rx));
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(claimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(itemRepository.findByPrescriptionVersionId(V1_ID)).thenReturn(List.of());

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                PrescriptionService.ClaimResult result = service.claim(jws, "idem-1", "vendor-9");

                assertThat(result.prescription().getRepeatsRemaining()).isEqualTo(1);
                assertThat(result.prescription().getStatus()).isEqualTo(PrescriptionStatus.SIGNED_ACTIVE);
                assertThat(result.claim().getVendorRef()).isEqualTo("vendor-9");
                assertThat(result.duplicateReplay()).isFalse();
                verify(outboxRepository).save(argThat(e -> "PRESCRIPTION_CLAIMED".equals(e.getEventType())));
            }
        }

        @Test
        void finalClaimExhaustsAndRevokesToken() {
            PrescriptionEntity rx = activeRx(1);
            String jws = tokenJwsFor("01TOKENBBBBBBBBBBBBBBBBBBB", V1_ID);
            PrescriptionTokenEntity token = activeToken(rx, jws);
            when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
            when(prescriptionRepository.findForClaim(RX_ID)).thenReturn(Optional.of(rx));
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(claimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(itemRepository.findByPrescriptionVersionId(V1_ID)).thenReturn(List.of());

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                service.claim(jws, null, null);

                assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.EXHAUSTED);
                assertThat(rx.getRepeatsRemaining()).isZero();
                assertThat(token.getStatus()).isEqualTo(PrescriptionTokenEntity.STATUS_REVOKED);
                assertThat(token.getRevokeReason()).isEqualTo("EXHAUSTED");
            }
        }

        @Test
        void claimAgainstSupersededVersionFailsRc7() {
            PrescriptionEntity rx = activeRx(2);
            rx.setCurrentVersionId("01VERSION2YYYYYYYYYYYYYYYY"); // amendment activated v2
            String jws = tokenJwsFor("01TOKENCCCCCCCCCCCCCCCCCCC", V1_ID);
            PrescriptionTokenEntity token = activeToken(rx, jws);
            token.setPrescriptionVersionId(V1_ID); // token still bound to superseded v1
            when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
            when(prescriptionRepository.findForClaim(RX_ID)).thenReturn(Optional.of(rx));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.claim(jws, null, null))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("superseded");
                assertThat(rx.getRepeatsRemaining()).isEqualTo(2); // counter untouched
            }
        }

        @Test
        void claimOnRevokedTokenRefused() {
            PrescriptionEntity rx = activeRx(2);
            String jws = tokenJwsFor("01TOKENDDDDDDDDDDDDDDDDDDD", V1_ID);
            PrescriptionTokenEntity token = activeToken(rx, jws);
            token.setStatus(PrescriptionTokenEntity.STATUS_REVOKED);
            when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.claim(jws, null, null))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("no longer claimable");
            }
        }

        @Test
        void tamperedTokenRefused() {
            PrescriptionEntity rx = activeRx(2);
            String minted = tokenJwsFor("01TOKENEEEEEEEEEEEEEEEEEEE", V1_ID);
            PrescriptionTokenEntity token = activeToken(rx, minted);
            when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
            // Same token id, different bytes (forged signature segment)
            String forged = minted.substring(0, minted.length() - 4) + "EVIL";

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.claim(forged, null, null))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("failed verification");
            }
        }

        @Test
        void idempotentReplayReturnsFirstClaimWithoutSecondDecrement() {
            PrescriptionClaimEntity existing = new PrescriptionClaimEntity();
            existing.setClaimId(UUID.randomUUID());
            existing.setPrescriptionId(RX_ID);
            existing.setPrescriptionVersionId(V1_ID);
            existing.setIdempotencyKey("idem-x");
            when(claimRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "idem-x"))
                    .thenReturn(Optional.of(existing));
            PrescriptionEntity rx = activeRx(2);
            when(prescriptionRepository.findByTenantIdAndPrescriptionId(TENANT_ID, RX_ID))
                    .thenReturn(Optional.of(rx));
            when(itemRepository.findByPrescriptionVersionId(V1_ID)).thenReturn(List.of());

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                PrescriptionService.ClaimResult result =
                        service.claim(tokenJwsFor("01TOKENFFFFFFFFFFFFFFFFFFF", V1_ID), "idem-x", null);
                assertThat(result.duplicateReplay()).isTrue();
                assertThat(rx.getRepeatsRemaining()).isEqualTo(2); // no second decrement
                verify(prescriptionRepository, never()).findForClaim(any());
            }
        }

        @Test
        void releaseRestoresCounterAndReactivatesExhausted() {
            PrescriptionEntity rx = activeRx(0);
            rx.setStatus(PrescriptionStatus.EXHAUSTED);
            PrescriptionClaimEntity claim = new PrescriptionClaimEntity();
            UUID claimId = UUID.randomUUID();
            claim.setClaimId(claimId);
            claim.setPrescriptionId(RX_ID);
            claim.setPrescriptionVersionId(V1_ID);
            claim.setTenantId(TENANT_ID);
            when(claimRepository.findByTenantIdAndClaimId(TENANT_ID, claimId)).thenReturn(Optional.of(claim));
            when(prescriptionRepository.findForClaim(RX_ID)).thenReturn(Optional.of(rx));
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(claimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            PrescriptionVersionEntity v1 = new PrescriptionVersionEntity();
            v1.setPrescriptionVersionId(V1_ID);
            when(versionRepository.findById(V1_ID)).thenReturn(Optional.of(v1));
            when(tokenRepository.findByPrescriptionIdAndStatus(RX_ID, PrescriptionTokenEntity.STATUS_ACTIVE))
                    .thenReturn(Optional.empty());
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubSigner();

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                service.releaseClaim(claimId, "Episode cancelled before dispensing");

                assertThat(claim.getStatus()).isEqualTo(PrescriptionClaimEntity.STATUS_RELEASED);
                assertThat(rx.getRepeatsRemaining()).isEqualTo(1);
                assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.SIGNED_ACTIVE);
                verify(tokenRepository).save(any()); // token re-minted
                verify(outboxRepository).save(argThat(e -> "PRESCRIPTION_CLAIM_RELEASED".equals(e.getEventType())));
            }
        }
    }

    @Nested
    @DisplayName("Cancel")
    class Cancel {

        @Test
        void cancelRevokesActiveToken() {
            PrescriptionEntity rx = activeRx(2);
            when(prescriptionRepository.findByTenantIdAndPrescriptionId(TENANT_ID, RX_ID))
                    .thenReturn(Optional.of(rx));
            when(prescriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            PrescriptionTokenEntity token = activeToken(rx, tokenJwsFor("01TOKENGGGGGGGGGGGGGGGGGGG", V1_ID));
            when(tokenRepository.findByPrescriptionIdAndStatus(RX_ID, PrescriptionTokenEntity.STATUS_ACTIVE))
                    .thenReturn(Optional.of(token));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                service.cancel(RX_ID, "Therapy changed");
                assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
                assertThat(token.getStatus()).isEqualTo(PrescriptionTokenEntity.STATUS_REVOKED);
                assertThat(token.getRevokeReason()).isEqualTo("CANCELLED");
            }
        }
    }
}
