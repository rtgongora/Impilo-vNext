package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubstitutionServiceTest {

    @Mock private OrderLineRepository orderLineRepository;
    @Mock private OrderStateMachine stateMachine;
    @Mock private EventOutboxRepository outboxRepository;

    private SubstitutionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SubstitutionService(orderLineRepository, stateMachine, outboxRepository, objectMapper);
    }

    @Test
    void proposeSubstitution_rxOrder_setsMetadata() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setOrderType(OrderType.RX_FULFILLMENT_ORDER);
        order.setTenantId(UUID.randomUUID());

        OrderLineEntity line = new OrderLineEntity();
        line.setLineId("LINE123456789012345678");
        line.setOrderId("ORDER12345678901234567");
        line.setMsikaCoreCode("DRUG-001");

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(orderLineRepository.findById("LINE123456789012345678")).thenReturn(Optional.of(line));
        when(orderLineRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderLineEntity result = service.proposeSubstitution(
                "ORDER12345678901234567", "LINE123456789012345678",
                "DRUG-002", new BigDecimal("5.50"), "Out of stock", "pharmacist-1");

        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("substitutionProposed"));
        assertTrue(result.getMetadata().contains("DRUG-002"));
    }

    @Test
    void proposeSubstitution_nonRxOrder_throwsException() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setOrderType(OrderType.OTC_PRODUCT_ORDER);

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);

        assertThrows(IllegalArgumentException.class, () ->
                service.proposeSubstitution("ORDER12345678901234567", "LINE123456789012345678",
                        "DRUG-002", new BigDecimal("5.50"), "Reason", "actor-1"));
    }

    @Test
    void approveSubstitution_updatesCodeAndPrice() throws Exception {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setTenantId(UUID.randomUUID());

        String metadata = objectMapper.writeValueAsString(java.util.Map.of(
                "substitutionProposed", true,
                "originalCode", "DRUG-001",
                "substituteCode", "DRUG-002",
                "substitutePrice", "5.50",
                "reason", "Out of stock",
                "approved", false
        ));

        OrderLineEntity line = new OrderLineEntity();
        line.setLineId("LINE123456789012345678");
        line.setOrderId("ORDER12345678901234567");
        line.setMsikaCoreCode("DRUG-001");
        line.setQty(2);
        line.setUnitPrice(new BigDecimal("4.00"));
        line.setMetadata(metadata);

        when(orderLineRepository.findById("LINE123456789012345678")).thenReturn(Optional.of(line));
        when(orderLineRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderLineEntity result = service.approveSubstitution("ORDER12345678901234567", "LINE123456789012345678", "patient-1");

        assertEquals("DRUG-002", result.getMsikaCoreCode());
        assertEquals(new BigDecimal("5.50"), result.getUnitPrice());
        assertEquals(new BigDecimal("11.00"), result.getLineTotal());
    }
}
