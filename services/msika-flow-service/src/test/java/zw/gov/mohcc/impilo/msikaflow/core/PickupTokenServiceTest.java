package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PickupClaimTrust;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickupTokenServiceTest {

    @Mock private PickupTokenRepository tokenRepository;
    @Mock private OrderStateMachine stateMachine;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private MsikaPickupBiometricPolicyClient pickupBiometricPolicyClient;

    private PickupTokenService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PickupTokenService(
                tokenRepository, stateMachine, outboxRepository, objectMapper, pickupBiometricPolicyClient);
        lenient().doNothing().when(pickupBiometricPolicyClient).assertPickupClaimAllowed(any());
    }

    @Test
    void issueToken_readyForPickup_generatesTokenAndOtp() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setTenantId(UUID.randomUUID());

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(tokenRepository.findByOrderIdAndStatus("ORDER12345678901234567", PickupTokenStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID tenantId = UUID.randomUUID();
        PickupTokenService.PickupIssueResult result = service.issueToken("ORDER12345678901234567", "actor-1", tenantId);

        assertNotNull(result.tokenId());
        assertNotNull(result.rawToken());
        assertNotNull(result.rawOtp());
        assertEquals(6, result.rawOtp().length()); // 6-digit OTP
        assertTrue(result.expiresAt().isAfter(OffsetDateTime.now()));

        verify(tokenRepository).save(any(PickupTokenEntity.class));
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void issueToken_notReadyForPickup_throwsException() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PAID); // wrong status

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);

        assertThrows(IllegalStateException.class, () ->
                service.issueToken("ORDER12345678901234567", "actor-1", UUID.randomUUID()));
    }

    @Test
    void issueToken_revokesExistingActiveToken() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.READY_FOR_PICKUP);

        PickupTokenEntity existingToken = new PickupTokenEntity();
        existingToken.setId("OLDTOKEN1234567890123456");
        existingToken.setStatus(PickupTokenStatus.ACTIVE);

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(tokenRepository.findByOrderIdAndStatus("ORDER12345678901234567", PickupTokenStatus.ACTIVE))
                .thenReturn(Optional.of(existingToken));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.issueToken("ORDER12345678901234567", "actor-1", UUID.randomUUID());

        assertEquals(PickupTokenStatus.REVOKED, existingToken.getStatus());
        verify(tokenRepository, atLeast(2)).save(any()); // save revoked + save new
    }

    @Test
    void claimPickup_rateLimited_throwsException() {
        // Make MAX_CLAIM_ATTEMPTS_PER_HOUR (5) rapid attempts
        for (int i = 0; i < 5; i++) {
            try {
                service.claimPickup(
                        "invalid-token-" + i,
                        trust("rate-limited-actor", "PATIENT", UUID.randomUUID()));
            } catch (Exception ignored) {
                // Expected to fail on token lookup, but rate counter increments
            }
        }

        // 6th attempt should be rate limited
        assertThrows(IllegalStateException.class, () ->
                service.claimPickup("any-token", trust("rate-limited-actor", "PATIENT", UUID.randomUUID())));
    }

    private static PickupClaimTrust trust(String actorId, String actorType, UUID tenantId) {
        return new PickupClaimTrust(tenantId, UUID.randomUUID().toString(), actorId, actorType, null, "fp");
    }
}
