package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import zw.gov.mohcc.impilo.oros.domain.*;
import zw.gov.mohcc.impilo.oros.persistence.entity.AcknowledgementEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.AcknowledgementRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorklistService}.
 *
 * <p>Validates worklist filtering, priority sorting, and order
 * acceptance/rejection workflows.</p>
 */
@ExtendWith(MockitoExtension.class)
class WorklistServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private AcknowledgementRepository ackRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private WorklistService worklistService;
    private OrderStateMachine stateMachine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "lab-tech-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        stateMachine = new OrderStateMachine(
                orderRepository, orderItemRepository, outboxRepository, objectMapper,
                new OrderVersionWriter(
                        org.mockito.Mockito.mock(
                                zw.gov.mohcc.impilo.oros.persistence.repository.OrderVersionRepository.class),
                        orderItemRepository, objectMapper));
        worklistService = new WorklistService(
                orderRepository, ackRepository, outboxRepository, stateMachine, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private OrderEntity createOrderWithPriority(OrderPriority priority) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(OrderStateMachine.generateUlid());
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-001");
        order.setOrderType(OrderType.LAB);
        order.setPriority(priority);
        order.setStatus(OrderStatus.PLACED);
        order.setPlacedBy(ACTOR_ID);
        return order;
    }

    @Nested
    @DisplayName("Worklist Filtering")
    class WorklistFiltering {

        @Test
        @DisplayName("Filters by type and status when both provided")
        void filterByTypeAndStatus() {
            Page<OrderEntity> emptyPage = new PageImpl<>(List.of());
            when(orderRepository.findByFacilityIdAndOrderTypeAndStatus(
                    eq(FACILITY_ID), eq(OrderType.LAB), eq(OrderStatus.PLACED), any(Pageable.class)))
                    .thenReturn(emptyPage);

            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                Page<OrderEntity> result = worklistService.getWorklist(
                        FACILITY_ID, null, OrderType.LAB, OrderStatus.PLACED, null, PageRequest.of(0, 20));

                assertThat(result).isNotNull();
                verify(orderRepository).findByFacilityIdAndOrderTypeAndStatus(
                        eq(FACILITY_ID), eq(OrderType.LAB), eq(OrderStatus.PLACED), any(Pageable.class));
            }
        }

        @Test
        @DisplayName("Filters by facility only when no other criteria provided")
        void filterByFacilityOnly() {
            Page<OrderEntity> emptyPage = new PageImpl<>(List.of());
            when(orderRepository.findByFacilityId(eq(FACILITY_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                Page<OrderEntity> result = worklistService.getWorklist(
                        FACILITY_ID, null, null, null, null, PageRequest.of(0, 20));

                assertThat(result).isNotNull();
                verify(orderRepository).findByFacilityId(eq(FACILITY_ID), any(Pageable.class));
            }
        }
    }

    @Nested
    @DisplayName("Priority Sorting")
    class PrioritySorting {

        @Test
        @DisplayName("Worklist is sorted with priority ordering")
        void worklistSortedByPriority() {
            OrderEntity statOrder = createOrderWithPriority(OrderPriority.STAT);
            OrderEntity urgentOrder = createOrderWithPriority(OrderPriority.URGENT);
            OrderEntity asapOrder = createOrderWithPriority(OrderPriority.ASAP);
            OrderEntity routineOrder = createOrderWithPriority(OrderPriority.ROUTINE);

            // Verify the sort parameter includes priority
            Page<OrderEntity> page = new PageImpl<>(List.of(statOrder, urgentOrder, asapOrder, routineOrder));

            when(orderRepository.findByFacilityId(eq(FACILITY_ID), any(Pageable.class)))
                    .thenReturn(page);

            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                Page<OrderEntity> result = worklistService.getWorklist(
                        FACILITY_ID, null, null, null, null, PageRequest.of(0, 20));

                assertThat(result.getContent()).hasSize(4);

                // Verify that the repository call includes a sort with priority
                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(orderRepository).findByFacilityId(eq(FACILITY_ID), pageableCaptor.capture());

                Pageable captured = pageableCaptor.getValue();
                assertThat(captured.getSort().getOrderFor("priority")).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Accept/Reject Orders")
    class AcceptRejectOrders {

        @Test
        @DisplayName("acceptOrder transitions to ACCEPTED and creates DEPARTMENT ack")
        void acceptOrderCreatesAck() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderWithPriority(OrderPriority.ROUTINE);
                when(orderRepository.findByOrderId(order.getOrderId()))
                        .thenReturn(Optional.of(order));
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(ackRepository.save(any(AcknowledgementEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity accepted = worklistService.acceptOrder(order.getOrderId());

                assertThat(accepted.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

                // Verify DEPARTMENT ack was created
                ArgumentCaptor<AcknowledgementEntity> ackCaptor = ArgumentCaptor.forClass(AcknowledgementEntity.class);
                verify(ackRepository).save(ackCaptor.capture());

                AcknowledgementEntity ack = ackCaptor.getValue();
                assertThat(ack.getAckType()).isEqualTo(AckType.DEPARTMENT);
                assertThat(ack.getActorId()).isEqualTo(ACTOR_ID);
                assertThat(ack.getOrderId()).isEqualTo(order.getOrderId());
            }
        }

        @Test
        @DisplayName("rejectOrder transitions to REJECTED and includes reason in notes")
        void rejectOrderWithReason() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderWithPriority(OrderPriority.ROUTINE);
                when(orderRepository.findByOrderId(order.getOrderId()))
                        .thenReturn(Optional.of(order));
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity rejected = worklistService.rejectOrder(order.getOrderId(), "Invalid specimen");

                assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
                assertThat(rejected.getClinicalNotes()).contains("REJECTION: Invalid specimen");
            }
        }
    }
}
