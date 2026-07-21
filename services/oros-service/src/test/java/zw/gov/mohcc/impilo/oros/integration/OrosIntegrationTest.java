package zw.gov.mohcc.impilo.oros.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.core.*;
import zw.gov.mohcc.impilo.oros.domain.*;
import zw.gov.mohcc.impilo.oros.persistence.entity.*;
import zw.gov.mohcc.impilo.oros.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test for the complete OROS order lifecycle.
 *
 * <p>Simulates the full flow from order placement through to completion
 * using mocked repositories. Validates state transitions are correct
 * at each step of the lifecycle.</p>
 *
 * <p>Flow tested:
 * Place order -> Route -> Accept -> Start worksteps -> Complete worksteps ->
 * Post result -> Acknowledge -> Complete order.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrosIntegrationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private WorkstepRepository workstepRepository;
    @Mock private RoutingRepository routingRepository;
    @Mock private CapabilityRepository capabilityRepository;
    @Mock private ReconcileQueueRepository reconcileQueueRepository;
    @Mock private AcknowledgementRepository ackRepository;
    @Mock private ResultRepository resultRepository;
    @Mock private SlaTimerRepository slaTimerRepository;

    private OrderStateMachine stateMachine;
    private WorkstepEngine workstepEngine;
    private RoutingEngine routingEngine;
    private WorklistService worklistService;
    private ResultService resultService;
    private AcknowledgementService ackService;
    private SlaService slaService;

    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "dr-integration-test";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = OrosTestObjectMapper.create();

        stateMachine = new OrderStateMachine(
                orderRepository, orderItemRepository, outboxRepository, objectMapper);

        workstepEngine = new WorkstepEngine(
                workstepRepository, outboxRepository, objectMapper);

        OrosProperties properties = new OrosProperties();
        properties.getRouting().setDefaultMode("INTERNAL");
        routingEngine = new RoutingEngine(
                routingRepository, capabilityRepository, reconcileQueueRepository,
                outboxRepository, properties, objectMapper);

        worklistService = new WorklistService(
                orderRepository, ackRepository, outboxRepository, stateMachine, objectMapper);

        resultService = new ResultService(
                resultRepository, stateMachine, outboxRepository, objectMapper);

        ackService = new AcknowledgementService(
                ackRepository, orderRepository, stateMachine, outboxRepository, properties, objectMapper);

        slaService = new SlaService(slaTimerRepository, orderRepository, outboxRepository, properties, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("Complete order lifecycle: place -> route -> accept -> worksteps -> result -> ack -> complete")
    void completeOrderLifecycle() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            // Configure repository mocks
            when(orderRepository.save(any(OrderEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(orderItemRepository.save(any(OrderItemEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(workstepRepository.save(any(WorkstepEntity.class)))
                    .thenAnswer(invocation -> {
                        WorkstepEntity ws = invocation.getArgument(0);
                        if (ws.getWorkstepId() == null) {
                            ws.setWorkstepId(UUID.randomUUID());
                        }
                        return ws;
                    });
            when(routingRepository.save(any(RoutingEntity.class)))
                    .thenAnswer(invocation -> {
                        RoutingEntity r = invocation.getArgument(0);
                        if (r.getRouteId() == null) {
                            r.setRouteId(UUID.randomUUID());
                        }
                        return r;
                    });
            when(ackRepository.save(any(AcknowledgementEntity.class)))
                    .thenAnswer(invocation -> {
                        AcknowledgementEntity a = invocation.getArgument(0);
                        if (a.getAckId() == null) {
                            a.setAckId(UUID.randomUUID());
                        }
                        return a;
                    });
            when(resultRepository.save(any(ResultEntity.class)))
                    .thenAnswer(invocation -> {
                        ResultEntity r = invocation.getArgument(0);
                        if (r.getResultId() == null) {
                            r.setResultId(UUID.randomUUID());
                        }
                        return r;
                    });
            when(slaTimerRepository.save(any(SlaTimerEntity.class)))
                    .thenAnswer(invocation -> {
                        SlaTimerEntity t = invocation.getArgument(0);
                        if (t.getTimerId() == null) {
                            t.setTimerId(UUID.randomUUID());
                        }
                        return t;
                    });

            // No facility-specific capability => default INTERNAL
            when(capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(capabilityRepository.findByTenantIdAndFacilityIdIsNullAndOrderType(any(), any()))
                    .thenReturn(Optional.empty());

            // ======== Step 1: Place Order ========
            List<OrderStateMachine.OrderItemData> items = List.of(
                    OrderStateMachine.OrderItemData.lab("CBC", "Complete Blood Count", 1, null, "BLOOD", null)
            );

            OrderEntity order = stateMachine.placeOrder(
                    FACILITY_ID, "CPID-INTEGRATION-001", OrderType.LAB,
                    OrderPriority.URGENT, "ZIBO-CBC", "ENC-INT-001",
                    "Integration test order", null, null, items);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(order.getOrderId()).hasSize(26);
            String orderId = order.getOrderId();

            // ======== Step 2: Route Order ========
            RoutingEntity route = routingEngine.routeOrder(order);
            assertThat(route.getRouteTarget()).isEqualTo(RouteTarget.INTERNAL);
            assertThat(route.getStatus()).isEqualTo(RouteStatus.SENT);

            // ======== Step 3: Create Worksteps ========
            List<WorkstepEntity> worksteps = workstepEngine.createWorkstepsForOrder(order);
            assertThat(worksteps).hasSize(6); // LAB: ORDER_PLACED, SPECIMEN_COLLECTION, SPECIMEN_RECEIVED, ANALYSIS, REPORTING, CLOSE_OUT
            assertThat(worksteps.get(0).getStatus()).isEqualTo(WorkstepStatus.COMPLETED); // ORDER_PLACED auto-completed
            assertThat(worksteps.get(1).getStatus()).isEqualTo(WorkstepStatus.PENDING);

            // ======== Step 4: Start SLA Timer ========
            SlaTimerEntity timer = slaService.startTimer(order.getOrderId(), "ORDER_PLACED", 0);
            assertThat(timer.getOrderId()).isEqualTo(orderId);
            assertThat(timer.getTargetAt()).isNotNull();
            assertThat(timer.isBreached()).isFalse();

            // ======== Step 5: Accept Order ========
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));
            OrderEntity accepted = worklistService.acceptOrder(orderId);
            assertThat(accepted.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

            // ======== Step 6: Start/Complete Worksteps ========
            // Start specimen collection (index 1)
            WorkstepEntity specimenStep = worksteps.get(1);
            when(workstepRepository.findById(specimenStep.getWorkstepId()))
                    .thenReturn(Optional.of(specimenStep));

            WorkstepEntity started = workstepEngine.startWorkstep(specimenStep.getWorkstepId());
            assertThat(started.getStatus()).isEqualTo(WorkstepStatus.IN_PROGRESS);
            assertThat(started.getAssignedTo()).isEqualTo(ACTOR_ID);

            WorkstepEntity completed = workstepEngine.completeWorkstep(specimenStep.getWorkstepId());
            assertThat(completed.getStatus()).isEqualTo(WorkstepStatus.COMPLETED);
            assertThat(completed.getCompletedAt()).isNotNull();

            // Transition order to IN_PROGRESS
            order = stateMachine.transition(order, OrderStatus.IN_PROGRESS);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

            // Complete remaining worksteps (specimen_received, analysis, reporting)
            for (int i = 2; i <= 4; i++) {
                WorkstepEntity step = worksteps.get(i);
                when(workstepRepository.findById(step.getWorkstepId()))
                        .thenReturn(Optional.of(step));
                workstepEngine.startWorkstep(step.getWorkstepId());
                workstepEngine.completeWorkstep(step.getWorkstepId());
            }

            // ======== Step 7: Post Result ========
            ResultEntity result = resultService.postResult(
                    orderId, ResultKind.LAB, "{\"wbc\": 7.5, \"rbc\": 4.8}",
                    "ZIBO-CBC-RESULT", null, false);

            assertThat(result.getOrderId()).isEqualTo(orderId);
            assertThat(result.getKind()).isEqualTo(ResultKind.LAB);
            assertThat(result.isCritical()).isFalse();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.RESULT_AVAILABLE);

            // ======== Step 8: Acknowledge Result ========
            AcknowledgementEntity ack = ackService.acknowledge(
                    orderId, AckType.CLINICIAN, "Results reviewed and normal");

            assertThat(ack.getAckType()).isEqualTo(AckType.CLINICIAN);
            assertThat(ack.getActorId()).isEqualTo(ACTOR_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REVIEWED);

            // ======== Step 9: Release and Complete ========
            order = stateMachine.transition(order, OrderStatus.RELEASED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.RELEASED);

            // Complete close-out workstep
            WorkstepEntity closeOut = worksteps.get(5);
            when(workstepRepository.findById(closeOut.getWorkstepId()))
                    .thenReturn(Optional.of(closeOut));
            workstepEngine.startWorkstep(closeOut.getWorkstepId());
            workstepEngine.completeWorkstep(closeOut.getWorkstepId());

            order = stateMachine.transition(order, OrderStatus.COMPLETED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

            // ======== Verify Final State ========
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

            // Verify outbox events were written for major lifecycle steps
            verify(outboxRepository, atLeast(8)).save(any(EventOutboxEntity.class));

            // Verify all worksteps are completed
            for (WorkstepEntity step : worksteps) {
                assertThat(step.getStatus()).isEqualTo(WorkstepStatus.COMPLETED);
            }
        }
    }
}
