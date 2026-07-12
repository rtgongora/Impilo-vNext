package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.api.dto.SubstitutionView;
import zw.gov.mohcc.impilo.msikaflow.domain.LineItemKind;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderLineRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubstitutionListRejectTest {

    @Mock OrderLineRepository orderLineRepository;
    @Mock OrderStateMachine stateMachine;
    @Mock EventOutboxRepository outboxRepository;

    SubstitutionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ORDER = "ORDER12345678901234567";
    private static final String LINE = "LINE1234567890123456789";

    @BeforeEach
    void setUp() {
        service = new SubstitutionService(orderLineRepository, stateMachine, outboxRepository, objectMapper);
    }

    private OrderLineEntity proposedLine() {
        OrderLineEntity line = new OrderLineEntity();
        line.setLineId(LINE);
        line.setOrderId(ORDER);
        line.setKind(LineItemKind.PRODUCT);
        line.setQty(1);
        line.setMsikaCoreCode("MED-A");
        line.setMetadata("{\"substitutionProposed\":true,\"originalCode\":\"MED-A\","
                + "\"substituteCode\":\"MED-B\",\"substitutePrice\":\"5.00\",\"reason\":\"stockout\","
                + "\"proposedBy\":\"pharm-1\",\"approved\":false}");
        return line;
    }

    @Test
    void list_returnsPendingProposal() {
        when(orderLineRepository.findByOrderId(ORDER)).thenReturn(List.of(proposedLine()));
        List<SubstitutionView> views = service.listSubstitutions(ORDER, null);
        assertEquals(1, views.size());
        assertEquals("PENDING", views.get(0).status());
        assertEquals("MED-B", views.get(0).substituteCode());
    }

    @Test
    void list_statusFilterExcludesNonMatching() {
        when(orderLineRepository.findByOrderId(ORDER)).thenReturn(List.of(proposedLine()));
        assertTrue(service.listSubstitutions(ORDER, "APPROVED").isEmpty());
    }

    @Test
    void reject_marksRejected_publishesEvent() {
        OrderLineEntity line = proposedLine();
        OrderEntity order = new OrderEntity();
        order.setOrderId(ORDER);
        order.setTenantId(UUID.randomUUID());
        when(orderLineRepository.findById(LINE)).thenReturn(Optional.of(line));
        when(orderLineRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stateMachine.getOrder(ORDER)).thenReturn(order);
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.rejectSubstitution(ORDER, LINE, "ops-1", "not clinically equivalent");

        assertTrue(line.getMetadata().contains("\"rejected\":true"));
        verify(outboxRepository).save(any());
    }

    @Test
    void reject_alreadyApproved_throws() {
        OrderLineEntity line = proposedLine();
        line.setMetadata(line.getMetadata().replace("\"approved\":false", "\"approved\":true"));
        when(orderLineRepository.findById(LINE)).thenReturn(Optional.of(line));

        assertThrows(IllegalStateException.class,
                () -> service.rejectSubstitution(ORDER, LINE, "ops-1", "x"));
    }
}
