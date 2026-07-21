package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalOrderIntakeServiceTest {

    @Mock private OrderStateMachine stateMachine;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderSubmissionService submissionService;
    @Mock private RoutingEngine routingEngine;
    @Mock private WorkstepEngine workstepEngine;
    @Mock private SlaService slaService;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();

    private ExternalOrderIntakeService service() {
        return new ExternalOrderIntakeService(stateMachine, orderRepository, submissionService,
                routingEngine, workstepEngine, slaService);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT, "gw-1", "SYSTEM", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private ExternalOrderIntake input() {
        return new ExternalOrderIntake("CPID-1", OrderType.IMAGING, OrderPriority.STAT,
                "CHEST-XR", "Chest X-Ray", "Cough", "urn:ext|EXT-1");
    }

    private OrderEntity order(String id, OrderStatus status) {
        OrderEntity o = new OrderEntity();
        o.setOrderId(id);
        o.setStatus(status);
        return o;
    }

    @Test
    @DisplayName("create makes an EXTERNAL order, submits, and orchestrates routing/worksteps/SLA")
    void createsAndOrchestrates() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(orderRepository.findByTenantIdAndExternalOrderRef(TENANT, "urn:ext|EXT-1")).thenReturn(Optional.empty());
            OrderEntity draft = order("ORD-NEW", OrderStatus.DRAFT);
            when(stateMachine.createDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(draft);
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(submissionService.submit("ORD-NEW")).thenReturn(order("ORD-NEW", OrderStatus.PLACED));

            OrderEntity result = service().create(input());

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(draft.getExternalOrderRef()).isEqualTo("urn:ext|EXT-1");
            verify(stateMachine).createDraft(eq(FACILITY), eq("CPID-1"), eq(OrderType.IMAGING),
                    eq(OrderPriority.STAT), eq(RequestSource.EXTERNAL), any(), any(), any(), eq("Cough"),
                    any(), any(), any(), any(), any());
            verify(routingEngine).routeOrder(any());
            verify(workstepEngine).createWorkstepsForOrder(any());
            verify(slaService).startTimer(eq("ORD-NEW"), any(), anyInt());
        }
    }

    @Test
    @DisplayName("external intake generalises to non-imaging categories (LAB paper/external order)")
    void createsLabExternalOrder() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            ExternalOrderIntake labInput = new ExternalOrderIntake("CPID-2", OrderType.LAB,
                    OrderPriority.ROUTINE, "FBC", "Full Blood Count", "anaemia", "urn:ext|LAB-9");
            when(orderRepository.findByTenantIdAndExternalOrderRef(TENANT, "urn:ext|LAB-9")).thenReturn(Optional.empty());
            OrderEntity draft = order("ORD-LAB", OrderStatus.DRAFT);
            when(stateMachine.createDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(draft);
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(submissionService.submit("ORD-LAB")).thenReturn(order("ORD-LAB", OrderStatus.PLACED));

            OrderEntity result = service().create(labInput);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
            verify(stateMachine).createDraft(eq(FACILITY), eq("CPID-2"), eq(OrderType.LAB),
                    eq(OrderPriority.ROUTINE), eq(RequestSource.EXTERNAL), any(), any(), any(), eq("anaemia"),
                    any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("create is idempotent on the external ref")
    void idempotent() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(orderRepository.findByTenantIdAndExternalOrderRef(TENANT, "urn:ext|EXT-1"))
                    .thenReturn(Optional.of(order("ORD-OLD", OrderStatus.PLACED)));

            assertThat(service().create(input()).getOrderId()).isEqualTo("ORD-OLD");
            verify(stateMachine, never()).createDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
