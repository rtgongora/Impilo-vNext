package zw.gov.mohcc.impilo.tshepo.audit.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditChainService;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.KeycloakEventReceiptRepository;

class KeycloakEventRecorderTest {
    private final AuditChainService chain = mock(AuditChainService.class);
    private final KeycloakEventReceiptRepository receipts = mock(KeycloakEventReceiptRepository.class);
    private final KeycloakEventRecorder recorder = new KeycloakEventRecorder(chain, receipts);

    @Test
    void claimsReceiptBeforeAppendingToChain() throws Exception {
        var event = new ObjectMapper().readTree("{\"id\":\"evt-1\",\"time\":42,\"type\":\"LOGIN\"}");

        assertTrue(recorder.record(UUID.randomUUID(), "user", event));

        InOrder order = inOrder(receipts, chain);
        order.verify(receipts).existsById("user:evt-1");
        order.verify(receipts).saveAndFlush(any());
        order.verify(chain).appendEvent(any());
    }

    @Test
    void replayDoesNotAppendAgain() throws Exception {
        var event = new ObjectMapper().readTree("{\"id\":\"evt-1\",\"type\":\"LOGIN\"}");
        when(receipts.existsById("user:evt-1")).thenReturn(true);

        assertFalse(recorder.record(UUID.randomUUID(), "user", event));

        verify(receipts, never()).saveAndFlush(any());
        verifyNoInteractions(chain);
    }
}
