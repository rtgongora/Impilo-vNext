package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriticalEscalationServiceTest {

    @Mock private ResultRepository resultRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private CriticalEscalationService service() {
        ObjectMapper om = OrosTestObjectMapper.create();
        return new CriticalEscalationService(resultRepository, orderRepository, outboxRepository, om, new OrosProperties());
    }

    private ResultEntity criticalResult(String orderId) {
        ResultEntity r = new ResultEntity();
        r.setResultId(UUID.randomUUID());
        r.setOrderId(orderId);
        r.setCritical(true);
        r.setCriticalReason("K+ 7.2");
        return r;
    }

    private OrderEntity order(String orderId) {
        OrderEntity o = new OrderEntity();
        o.setOrderId(orderId);
        o.setTenantId(UUID.randomUUID());
        o.setPatientCpid("CPID-1");
        o.setPlacedBy("dr-1");
        return o;
    }

    @Test
    @DisplayName("escalateOverdue marks escalatedAt and emits ACK_ESCALATION for each overdue result")
    void escalatesOverdue() {
        ResultEntity r = criticalResult("ORD-1");
        when(resultRepository.findByIsCriticalTrueAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndCreatedAtBefore(
                any(OffsetDateTime.class))).thenReturn(List.of(r));
        when(orderRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(order("ORD-1")));
        when(resultRepository.save(any(ResultEntity.class))).thenAnswer(i -> i.getArgument(0));

        int count = service().escalateOverdue(30);

        assertThat(count).isEqualTo(1);
        assertThat(r.getEscalatedAt()).isNotNull();
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    @DisplayName("results whose order is missing are skipped, not escalated")
    void skipsMissingOrder() {
        ResultEntity r = criticalResult("ORD-GONE");
        when(resultRepository.findByIsCriticalTrueAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndCreatedAtBefore(
                any(OffsetDateTime.class))).thenReturn(List.of(r));
        when(orderRepository.findByOrderId("ORD-GONE")).thenReturn(Optional.empty());

        int count = service().escalateOverdue(30);

        assertThat(count).isZero();
        verify(outboxRepository, never()).save(any());
    }
}
