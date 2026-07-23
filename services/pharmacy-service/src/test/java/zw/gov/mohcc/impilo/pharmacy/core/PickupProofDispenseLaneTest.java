package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.config.PharmacyProperties;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupVerificationGrade;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.PickupProofRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B16 — collector identity + verification grade on every claim (§12.4),
 * the named-recipient/SMS-reference path (server-side resolution, no token in
 * SMS, §8.11.3), and the uncollected-expiry escalation (episode cancelled +
 * stock return + coded event).
 */
@ExtendWith(MockitoExtension.class)
class PickupProofDispenseLaneTest {

    @Mock PickupProofRepository proofRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock BiometricVerificationClient biometricVerification;
    @Mock DispenseOrderRepository orderRepository;
    @Mock DispenseEngine dispenseEngine;
    @Mock StockLedgerService stockLedgerService;

    private PickupProofServiceImpl service;
    private HmacService hmacService;

    private static final String TOKEN = "OTP-654321";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hmacService = new HmacService("unit-test-secret-key");
        service = new PickupProofServiceImpl(proofRepository, outboxRepository, hmacService,
                new PharmacyProperties(), new ObjectMapper(), biometricVerification,
                orderRepository, dispenseEngine, stockLedgerService);
        TrustContextHolder.set(new TrustContext(TENANT_ID, "staff-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private PickupProofEntity pendingProof(PickupMethod method, String delegatedTo) {
        PickupProofEntity proof = new PickupProofEntity();
        proof.setProofId(UUID.randomUUID());
        proof.setDispenseOrderId(ORDER_ID);
        proof.setMethod(method);
        proof.setTokenHash(hmacService.computeLookupHash(TOKEN));
        proof.setStatus(PickupStatus.PENDING);
        proof.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        proof.setDelegatedTo(delegatedTo);
        return proof;
    }

    @Nested
    @DisplayName("Collector identity + verification grade")
    class Grades {

        @Test
        @DisplayName("token claim records collector identity + grade TOKEN_MATCH")
        void tokenClaimRecordsGrade() {
            PickupProofEntity proof = pendingProof(PickupMethod.OTP, null);
            when(proofRepository.findByTokenHashAndStatus(proof.getTokenHash(), PickupStatus.PENDING))
                    .thenReturn(Optional.of(proof));
            when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PickupProofEntity claimed = service.claimProof(TOKEN, "dev-1", "Jane Moyo",
                    null, null, null);

            assertThat(claimed.getStatus()).isEqualTo(PickupStatus.CLAIMED);
            assertThat(claimed.getCollectorIdentity()).isEqualTo("Jane Moyo");
            assertThat(claimed.getVerificationGrade()).isEqualTo(PickupVerificationGrade.TOKEN_MATCH);
        }

        @Test
        @DisplayName("delegate claim grades DELEGATE_TOKEN_MATCH, defaulting collector to the delegate")
        void delegateClaimGradesDelegate() {
            PickupProofEntity proof = pendingProof(PickupMethod.OTP, "caregiver-77");
            when(proofRepository.findByTokenHashAndStatus(proof.getTokenHash(), PickupStatus.PENDING))
                    .thenReturn(Optional.of(proof));
            when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PickupProofEntity claimed = service.claimProof(TOKEN, "dev-1", null, null, null, null);

            assertThat(claimed.getVerificationGrade())
                    .isEqualTo(PickupVerificationGrade.DELEGATE_TOKEN_MATCH);
            assertThat(claimed.getCollectorIdentity()).isEqualTo("caregiver-77");
        }

        @Test
        @DisplayName("biometric MATCH grades BIOMETRIC_MATCH")
        void biometricMatchGrades() {
            PickupProofEntity proof = pendingProof(PickupMethod.OTP, null);
            when(proofRepository.findByTokenHashAndStatus(proof.getTokenHash(), PickupStatus.PENDING))
                    .thenReturn(Optional.of(proof));
            when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(biometricVerification.verify(eq("subj-1"), eq("FINGERPRINT"), any()))
                    .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.97));

            PickupProofEntity claimed = service.claimProof(TOKEN, "dev-1", "Jane Moyo",
                    "subj-1", "FINGERPRINT", "cHJvYmU=");

            assertThat(claimed.getVerificationGrade())
                    .isEqualTo(PickupVerificationGrade.BIOMETRIC_MATCH);
        }
    }

    @Nested
    @DisplayName("Named-recipient / SMS-reference path")
    class SmsReference {

        @Test
        @DisplayName("SMS_REFERENCE proof requires a named recipient (fail-closed)")
        void smsRequiresNamedRecipient() {
            assertThatThrownBy(() ->
                    service.createProof(ORDER_ID, PickupMethod.SMS_REFERENCE, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("named recipient");
        }

        @Test
        @DisplayName("creation returns the opaque reference once and stores only hashes")
        void creationReturnsReferenceOnce() {
            when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PickupProofEntity proof = service.createProof(
                    ORDER_ID, PickupMethod.SMS_REFERENCE, null, "Jane Moyo");

            String reference = proof.getDeviceFingerprint(); // one-time stash
            assertThat(reference).hasSize(8).doesNotContain("0", "O", "1", "I", "L");
            assertThat(proof.getSmsReferenceHash())
                    .isEqualTo(hmacService.computeLookupHash(reference))
                    .isEqualTo(proof.getTokenHash());
            assertThat(proof.getNamedRecipient()).isEqualTo("Jane Moyo");
        }

        @Test
        @DisplayName("an SMS_REFERENCE proof cannot be claimed through the plain token path")
        void smsProofRejectsTokenPath() {
            PickupProofEntity proof = pendingProof(PickupMethod.SMS_REFERENCE, null);
            proof.setNamedRecipient("Jane Moyo");
            when(proofRepository.findByTokenHashAndStatus(proof.getTokenHash(), PickupStatus.PENDING))
                    .thenReturn(Optional.of(proof));

            assertThatThrownBy(() -> service.claimProof(TOKEN, "dev-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SMS-reference");
            verify(proofRepository, never()).save(any());
        }

        @Test
        @DisplayName("claim by reference with matching identity grades SMS_REFERENCE_ID_CHECK")
        void claimByReferenceHappyPath() {
            PickupProofEntity proof = pendingProof(PickupMethod.SMS_REFERENCE, null);
            proof.setNamedRecipient("Jane Moyo");
            proof.setSmsReferenceHash(hmacService.computeLookupHash("ABCD2345"));
            when(proofRepository.findBySmsReferenceHashAndStatus(
                    proof.getSmsReferenceHash(), PickupStatus.PENDING))
                    .thenReturn(Optional.of(proof));
            when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PickupProofEntity claimed = service.claimBySmsReference("abcd2345", "Jane Moyo", "dev-9");

            assertThat(claimed.getStatus()).isEqualTo(PickupStatus.CLAIMED);
            assertThat(claimed.getVerificationGrade())
                    .isEqualTo(PickupVerificationGrade.SMS_REFERENCE_ID_CHECK);
            assertThat(claimed.getCollectorIdentity()).isEqualTo("Jane Moyo");
        }

        @Test
        @DisplayName("identity mismatch with the named recipient is rejected (fail-closed)")
        void identityMismatchRejected() {
            PickupProofEntity proof = pendingProof(PickupMethod.SMS_REFERENCE, null);
            proof.setNamedRecipient("Jane Moyo");
            proof.setSmsReferenceHash(hmacService.computeLookupHash("ABCD2345"));
            when(proofRepository.findBySmsReferenceHashAndStatus(
                    proof.getSmsReferenceHash(), PickupStatus.PENDING))
                    .thenReturn(Optional.of(proof));

            assertThatThrownBy(() -> service.claimBySmsReference("ABCD2345", "Someone Else", "dev-9"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not match the named recipient");
        }

        @Test
        @DisplayName("blank collector identity is rejected before any lookup")
        void blankCollectorRejected() {
            assertThatThrownBy(() -> service.claimBySmsReference("ABCD2345", " ", "dev-9"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Collector identity is required");
            verifyNoInteractions(proofRepository);
        }
    }

    @Nested
    @DisplayName("Uncollected-expiry escalation")
    class ExpirySweep {

        private DispenseOrderEntity order(DispenseStatus status, UUID claimId) {
            DispenseOrderEntity order = new DispenseOrderEntity();
            order.setOrderId(ORDER_ID);
            order.setTenantId(TENANT_ID);
            order.setFacilityId(UUID.randomUUID());
            order.setPatientCpid("CPID-1");
            order.setOrosOrderId("01OROSORDERBBBBBBBBBBBBBBB");
            order.setStatus(status);
            order.setPrescriptionClaimId(claimId);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            return order;
        }

        private PickupProofEntity expiredProof() {
            PickupProofEntity proof = pendingProof(PickupMethod.OTP, null);
            proof.setExpiresAt(OffsetDateTime.now().minusMinutes(5));
            when(proofRepository.findByStatusAndExpiresAtBefore(eq(PickupStatus.PENDING), any()))
                    .thenReturn(List.of(proof));
            when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            return proof;
        }

        @Test
        @DisplayName("READY episode → cancelled, reversal attempted, PICKUP_EXPIRED_RETURN emitted")
        void readyEpisodeCancelled() {
            PickupProofEntity proof = expiredProof();
            order(DispenseStatus.READY, null);
            when(stockLedgerService.reverseMovements(eq("DISPENSE_ORDER"), eq(ORDER_ID.toString()), any()))
                    .thenReturn(List.of());

            service.expireProofs();

            assertThat(proof.getStatus()).isEqualTo(PickupStatus.EXPIRED);
            verify(dispenseEngine).cancelOrder(ORDER_ID);
            verify(outboxRepository).save(argThat(e ->
                    "PICKUP_EXPIRED_RETURN".equals(e.getEventType())
                            && e.getPayload().contains("\"priorStatus\":\"READY\"")));
        }

        @Test
        @DisplayName("DISPENSED episode with claim → stock returned + claim flagged for review")
        void dispensedEpisodeReturnsStockAndFlagsClaim() {
            expiredProof();
            UUID claimId = UUID.randomUUID();
            order(DispenseStatus.DISPENSED, claimId);
            when(stockLedgerService.reverseMovements(eq("DISPENSE_ORDER"), eq(ORDER_ID.toString()), any()))
                    .thenReturn(List.of(new zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity()));

            service.expireProofs();

            verify(stockLedgerService).reverseMovements(
                    "DISPENSE_ORDER", ORDER_ID.toString(), "PICKUP_EXPIRED_RETURN");
            verify(dispenseEngine).cancelOrder(ORDER_ID);
            verify(outboxRepository).save(argThat(e ->
                    "PICKUP_EXPIRED_RETURN".equals(e.getEventType())
                            && e.getPayload().contains("\"requiresClaimReview\":true")
                            && e.getPayload().contains(claimId.toString())));
        }

        @Test
        @DisplayName("in-progress episode (PICKING) is not escalated — proof expiry only")
        void inProgressNotEscalated() {
            PickupProofEntity proof = expiredProof();
            order(DispenseStatus.PICKING, null);

            service.expireProofs();

            assertThat(proof.getStatus()).isEqualTo(PickupStatus.EXPIRED);
            verify(dispenseEngine, never()).cancelOrder(any());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("escalation failure never kills the sweep")
        void escalationFailureContained() {
            PickupProofEntity proof = expiredProof();
            order(DispenseStatus.READY, null);
            when(stockLedgerService.reverseMovements(any(), any(), any()))
                    .thenThrow(new RuntimeException("ledger down"));

            service.expireProofs(); // must not throw

            assertThat(proof.getStatus()).isEqualTo(PickupStatus.EXPIRED);
        }
    }
}
