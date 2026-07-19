package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.config.PharmacyProperties;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.PickupProofRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Biometric collector-verification at dispense-pickup through the shared verification seam:
 * MATCH → the proof is marked collected via PickupMethod.BIOMETRIC; NO_MATCH → the collection
 * is rejected (nothing persisted); engine UNAVAILABLE → falls back to the token check so an
 * outage never blocks a patient collecting their medication.
 */
@ExtendWith(MockitoExtension.class)
class PickupProofBiometricTest {

    @Mock PickupProofRepository proofRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock BiometricVerificationClient biometricVerification;

    private PickupProofServiceImpl service;
    private static final String TOKEN = "OTP-123456";
    private static final UUID TENANT_ID = UUID.randomUUID();

    private HmacService hmacService;

    @BeforeEach
    void setUp() {
        hmacService = new HmacService("unit-test-secret-key");
        service = new PickupProofServiceImpl(proofRepository, outboxRepository, hmacService,
                new PharmacyProperties(), new ObjectMapper(), biometricVerification);
        TrustContextHolder.set(new TrustContext(TENANT_ID, "collector-1", "PATIENT", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private PickupProofEntity pendingProof() {
        PickupProofEntity proof = new PickupProofEntity();
        proof.setProofId(UUID.randomUUID());
        proof.setDispenseOrderId(UUID.randomUUID());
        proof.setMethod(PickupMethod.OTP);
        proof.setTokenHash(hmacService.computeLookupHash(TOKEN));
        proof.setStatus(PickupStatus.PENDING);
        proof.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        when(proofRepository.findByTokenHashAndStatus(proof.getTokenHash(), PickupStatus.PENDING))
                .thenReturn(Optional.of(proof));
        return proof;
    }

    @Test
    @DisplayName("MATCH → collected with method BIOMETRIC")
    void matchMarksCollectedWithBiometric() {
        PickupProofEntity proof = pendingProof();
        when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(biometricVerification.verify(eq("subj-collector-1"), eq("FINGERPRINT"), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.96));

        PickupProofEntity result = service.claimProof(TOKEN, "dev-1",
                "subj-collector-1", "FINGERPRINT", "cHJvYmU=");

        assertThat(result.getStatus()).isEqualTo(PickupStatus.CLAIMED);
        assertThat(result.getMethod()).isEqualTo(PickupMethod.BIOMETRIC);
    }

    @Test
    @DisplayName("NO_MATCH → collection rejected, nothing persisted")
    void noMatchRejectsCollection() {
        pendingProof();
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.02));

        assertThatThrownBy(() -> service.claimProof(TOKEN, "dev-1",
                "subj-collector-1", "FINGERPRINT", "cHJvYmU="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collection rejected");

        verify(proofRepository, never()).save(any());
    }

    @Test
    @DisplayName("engine UNAVAILABLE → falls back to token check (method unchanged)")
    void unavailableFallsBackToToken() {
        pendingProof();
        when(proofRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(BiometricVerificationClient.Decision.unavailable());

        PickupProofEntity result = service.claimProof(TOKEN, "dev-1",
                "subj-collector-1", "FINGERPRINT", "cHJvYmU=");

        assertThat(result.getStatus()).isEqualTo(PickupStatus.CLAIMED);
        assertThat(result.getMethod())
                .as("not marked biometric on an outage")
                .isEqualTo(PickupMethod.OTP);
    }
}
