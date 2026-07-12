package zw.gov.mohcc.impilo.msikaflow.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderStatus;
import zw.gov.mohcc.impilo.msikaflow.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Locks the honest Rx-token attach: a token is attached only when share-slip-service
 * confirms it valid; an invalid token is a 422 and nothing is persisted; an
 * unreachable verifier (502) propagates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RxTokenServiceTest {

    @Mock ShareSlipClient shareSlipClient;
    @Mock OrderStateMachine stateMachine;
    @Mock OrderRepository orderRepository;

    RxTokenService service;

    private static final String ORDER = "ORDER12345678901234567";

    @BeforeEach
    void setUp() {
        service = new RxTokenService(shareSlipClient, stateMachine, orderRepository);
    }

    private OrderEntity order() {
        OrderEntity o = new OrderEntity();
        o.setOrderId(ORDER);
        o.setStatus(OrderStatus.CREATED);
        return o;
    }

    @Test
    void attach_validToken_persistsRefAndRecordsEvent() {
        OrderEntity o = order();
        when(stateMachine.getOrder(ORDER)).thenReturn(o);
        when(shareSlipClient.verify("tok-1"))
                .thenReturn(new ShareSlipClient.ShareSlipVerification(true, "ACTIVE", "PRESCRIPTION"));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderEntity result = service.attachToken(ORDER, "tok-1", "actor-1", "PATIENT");

        assertEquals("tok-1", result.getRxTokenRef());
        assertEquals("PRESCRIPTION", result.getRxTokenSubjectType());
        verify(stateMachine).recordAuxiliaryEvent(eq(ORDER), eq("actor-1"), eq("PATIENT"),
                eq("RX_TOKEN_ATTACHED"), any());
    }

    @Test
    void attach_invalidToken_throws422_nothingPersisted() {
        OrderEntity o = order();
        when(stateMachine.getOrder(ORDER)).thenReturn(o);
        when(shareSlipClient.verify("tok-bad"))
                .thenReturn(new ShareSlipClient.ShareSlipVerification(false, "EXPIRED", null));

        assertThrows(ValidationFailedException.class,
                () -> service.attachToken(ORDER, "tok-bad", "actor-1", "PATIENT"));
        verify(orderRepository, never()).save(any());
        verify(stateMachine, never()).recordAuxiliaryEvent(any(), any(), any(), any(), any());
    }

    @Test
    void attach_verifierUnreachable_propagates502() {
        OrderEntity o = order();
        when(stateMachine.getOrder(ORDER)).thenReturn(o);
        when(shareSlipClient.verify("tok-1"))
                .thenThrow(new UpstreamUnavailableException("share-slip-service unreachable"));

        assertThrows(UpstreamUnavailableException.class,
                () -> service.attachToken(ORDER, "tok-1", "actor-1", "PATIENT"));
        verify(orderRepository, never()).save(any());
    }
}
