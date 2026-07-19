package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PickupClaimTrust;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.PickupTokenStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.PickupTokenEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.PickupTokenRepository;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Optional biometric collector-verify layered over the pickup token/OTP proof at
 * redemption: MATCH → claim marked biometric-verified; NO_MATCH → redemption rejected
 * (nothing claimed); engine UNAVAILABLE → falls back to the token/OTP proof (a valid
 * collection is never blocked by a biometric outage); absent fields → unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PickupTokenServiceBiometricTest {

    @Mock private PickupTokenRepository tokenRepository;
    @Mock private OrderStateMachine stateMachine;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private MsikaPickupBiometricPolicyClient pickupBiometricPolicyClient;
    @Mock private BiometricVerificationClient biometricVerification;

    private PickupTokenService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ORDER = "ORDER12345678901234567";

    @BeforeEach
    void setUp() {
        service = new PickupTokenService(
                tokenRepository, stateMachine, outboxRepository, objectMapper, pickupBiometricPolicyClient,
                biometricVerification);
        doNothing().when(pickupBiometricPolicyClient).assertPickupClaimAllowed(any());

        PickupTokenEntity token = new PickupTokenEntity();
        token.setId("TKN1234567890123456789012");
        token.setOrderId(ORDER);
        token.setStatus(PickupTokenStatus.ACTIVE);
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderEntity order = new OrderEntity();
        order.setOrderId(ORDER);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        when(stateMachine.getOrder(ORDER)).thenReturn(order);
    }

    private PickupClaimTrust trust() {
        return new PickupClaimTrust(UUID.randomUUID(), UUID.randomUUID().toString(),
                "collector-1", "STAFF", null, "fp");
    }

    @Test
    @DisplayName("MATCH → redemption claimed and marked biometric-verified")
    void matchMarksBiometricVerified() {
        when(biometricVerification.verify(eq("subj-client-1"), eq("FINGERPRINT"), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.98));

        var result = service.claimPickup("raw-token", trust(),
                "subj-client-1", "FINGERPRINT", "cHJvYmU=");

        assertTrue(result.biometricVerified());
        verify(stateMachine).transition(eq(ORDER), eq(OrderStatus.COLLECTED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("NO_MATCH → redemption rejected, nothing claimed")
    void noMatchRejects() {
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.1));

        assertThrows(IllegalStateException.class, () -> service.claimPickup("raw-token", trust(),
                "subj-client-1", "FINGERPRINT", "cHJvYmU="));

        verify(tokenRepository, never()).save(any());
        verify(stateMachine, never()).transition(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("engine UNAVAILABLE → falls back to token/OTP proof (claim completes, not biometric-verified)")
    void unavailableFallsBack() {
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(BiometricVerificationClient.Decision.unavailable());

        var result = service.claimPickup("raw-token", trust(),
                "subj-client-1", "FINGERPRINT", "cHJvYmU=");

        assertFalse(result.biometricVerified());
        verify(stateMachine).transition(eq(ORDER), eq(OrderStatus.COLLECTED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("absent biometric fields → unchanged token/OTP behaviour, verify never called")
    void absentFieldsUnchanged() {
        var result = service.claimPickup("raw-token", trust());

        assertFalse(result.biometricVerified());
        verify(biometricVerification, never()).verify(any(), any(), any());
        verify(stateMachine).transition(eq(ORDER), eq(OrderStatus.COLLECTED), any(), any(), any(), any());
    }
}
