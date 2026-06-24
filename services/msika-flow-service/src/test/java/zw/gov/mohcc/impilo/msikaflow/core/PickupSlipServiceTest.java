package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.PickupTokenStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.PickupTokenEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.PickupTokenRepository;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for the order-native pickup-slip PDF (G034): the PDF renderer produces real PDF bytes,
 * and {@link PickupTokenService#resolveSlipContext} validates a client-held token against the
 * stored hash without persisting it.
 */
@ExtendWith(MockitoExtension.class)
class PickupSlipServiceTest {

    @Mock private PickupTokenRepository tokenRepository;
    @Mock private OrderStateMachine stateMachine;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private MsikaPickupBiometricPolicyClient pickupBiometricPolicyClient;

    private PickupTokenService tokenService;
    private final PickupSlipPdfService pdfService = new PickupSlipPdfService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ORDER = "ORDER12345678901234567";

    @BeforeEach
    void setUp() {
        tokenService = new PickupTokenService(
                tokenRepository, stateMachine, outboxRepository, objectMapper, pickupBiometricPolicyClient);
    }

    // ----- PDF renderer -----

    @Test
    void rendersRealPdfWithToken() throws IOException {
        byte[] pdf = pdfService.generatePickupSlipPdf(
                ORDER, "TKN-1", OffsetDateTime.now().plusHours(48), "ACTIVE", "raw-token-abc123");
        assertTrue(pdf.length > 200);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void rendersRealPdfWithoutToken() throws IOException {
        byte[] pdf = pdfService.generatePickupSlipPdf(
                ORDER, "TKN-1", OffsetDateTime.now().plusHours(48), "ACTIVE", null);
        assertTrue(pdf.length > 200);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    // ----- slip-context resolution -----

    private PickupTokenEntity issueAndCapture() {
        OrderEntity order = new OrderEntity();
        order.setOrderId(ORDER);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setTenantId(UUID.randomUUID());
        when(stateMachine.getOrder(ORDER)).thenReturn(order);
        when(tokenRepository.findByOrderIdAndStatus(ORDER, PickupTokenStatus.ACTIVE)).thenReturn(Optional.empty());
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PickupTokenService.PickupIssueResult result = tokenService.issueToken(ORDER, "actor-1", UUID.randomUUID());

        ArgumentCaptor<PickupTokenEntity> captor = ArgumentCaptor.forClass(PickupTokenEntity.class);
        org.mockito.Mockito.verify(tokenRepository).save(captor.capture());
        PickupTokenEntity saved = captor.getValue();
        // attach the raw token to the entity via a side-channel for the test's convenience
        saved.setClaimMeta(result.rawToken());
        return saved;
    }

    @Test
    void resolveSlipContext_verifiesMatchingToken() {
        PickupTokenEntity saved = issueAndCapture();
        String rawToken = saved.getClaimMeta();
        when(tokenRepository.findByOrderIdAndStatus(ORDER, PickupTokenStatus.ACTIVE))
                .thenReturn(Optional.of(saved));

        PickupTokenService.PickupSlipContext ctx = tokenService.resolveSlipContext(ORDER, rawToken);

        assertTrue(ctx.tokenVerified());
        assertEquals(saved.getId(), ctx.tokenId());
        assertEquals("ACTIVE", ctx.status());
    }

    @Test
    void resolveSlipContext_rejectsMismatchedToken() {
        PickupTokenEntity saved = issueAndCapture();
        when(tokenRepository.findByOrderIdAndStatus(ORDER, PickupTokenStatus.ACTIVE))
                .thenReturn(Optional.of(saved));
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.resolveSlipContext(ORDER, "not-the-real-token"));
    }

    @Test
    void resolveSlipContext_withoutToken_isUnverifiedNotRejected() {
        PickupTokenEntity saved = issueAndCapture();
        when(tokenRepository.findByOrderIdAndStatus(ORDER, PickupTokenStatus.ACTIVE))
                .thenReturn(Optional.of(saved));
        PickupTokenService.PickupSlipContext ctx = tokenService.resolveSlipContext(ORDER, null);
        assertFalse(ctx.tokenVerified());
    }

    @Test
    void resolveSlipContext_noActiveToken_throws() {
        when(tokenRepository.findByOrderIdAndStatus("MISSING", PickupTokenStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.resolveSlipContext("MISSING", null));
    }
}
