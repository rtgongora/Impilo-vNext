package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BloodOrderCallbackService} — the OROS sink for MADI blood-bank callbacks.
 */
@ExtendWith(MockitoExtension.class)
class BloodOrderCallbackServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ResultService resultService;
    @Mock private EventOutboxRepository outboxRepository;

    private BloodOrderCallbackService service;

    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        service = new BloodOrderCallbackService(orderRepository, resultService,
                outboxRepository, OrosTestObjectMapper.create());
    }

    private OrderEntity order() {
        OrderEntity o = new OrderEntity();
        o.setOrderId(ORDER_ID);
        o.setTenantId(UUID.randomUUID());
        o.setOrderType(OrderType.BLOOD_BANK);
        o.setPatientCpid("CPID-1");
        return o;
    }

    @Test
    @DisplayName("submitted records the MADI ref and emits an event")
    void submitted() {
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order()));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.submitted(ORDER_ID, "MADI-77");

        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    @DisplayName("incompatible crossmatch result is flagged critical")
    void crossmatchIncompatibleCritical() {
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order()));

        service.crossmatchResult(ORDER_ID, "INCOMPATIBLE", "ABO mismatch");

        verify(resultService).postResult(eq(ORDER_ID), eq(ResultKind.LAB), anyString(),
                isNull(), isNull(), eq(true));
    }

    @Test
    @DisplayName("compatible crossmatch result is not critical")
    void crossmatchCompatible() {
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order()));

        service.crossmatchResult(ORDER_ID, "COMPATIBLE", null);

        verify(resultService).postResult(eq(ORDER_ID), eq(ResultKind.LAB), anyString(),
                isNull(), isNull(), eq(false));
    }

    @Test
    @DisplayName("adverse transfusion outcome is flagged critical")
    void transfusionAdverse() {
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order()));

        service.transfusionOutcome(ORDER_ID, "STOPPED_ADVERSE", "febrile reaction");

        verify(resultService).postResult(eq(ORDER_ID), eq(ResultKind.DOCUMENT), anyString(),
                isNull(), isNull(), eq(true));
    }
}
