package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.core.workflow.ImagingFulfilmentWorkflow;
import zw.gov.mohcc.impilo.oros.core.workflow.LabWorkflow;
import zw.gov.mohcc.impilo.oros.core.workflow.ProcedureWorkflow;
import zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FulfilmentWorkflowService} — the category-agnostic owner of the
 * generalised {@code workflow_state} transitions for lab/procedure (and imaging lockstep).
 */
@ExtendWith(MockitoExtension.class)
class FulfilmentWorkflowServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FulfilmentWorkflowService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        WorkflowGuardRegistry guards = new WorkflowGuardRegistry(List.of(
                new ImagingFulfilmentWorkflow(), new LabWorkflow(), new ProcedureWorkflow()));
        service = new FulfilmentWorkflowService(orderRepository, outboxRepository, objectMapper, guards);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "lab-tech-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private OrderEntity order(OrderType type, String workflowState) {
        OrderEntity o = new OrderEntity();
        o.setOrderId(ORDER_ID);
        o.setTenantId(TENANT_ID);
        o.setFacilityId(FACILITY_ID);
        o.setPatientCpid("CPID-001");
        o.setOrderType(type);
        o.setStatus(OrderStatus.IN_PROGRESS);
        o.setWorkflowState(workflowState);
        return o;
    }

    @Test
    @DisplayName("initialize sets the lab entry state and writes a WORKFLOW_STATE_RECEIVED event")
    void initializeLab() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            OrderEntity o = order(OrderType.LAB, null);

            service.initialize(o, "submitted");

            assertThat(o.getWorkflowState()).isEqualTo("RECEIVED");
            assertThat(o.getImagingState()).isNull();
            ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(ev.capture());
            assertThat(ev.getValue().getEventType()).isEqualTo("WORKFLOW_STATE_RECEIVED");
        }
    }

    @Test
    @DisplayName("initialize is a no-op for a category without a guard (pharmacy)")
    void initializePharmacyNoop() {
        OrderEntity o = order(OrderType.PHARMACY, null);
        service.initialize(o, "submitted");
        assertThat(o.getWorkflowState()).isNull();
        verifyNoInteractions(outboxRepository);
    }

    @Test
    @DisplayName("transition validates against the lab guard, persists, and emits the event")
    void transitionLab() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            OrderEntity o = order(OrderType.LAB, "RECEIVED_IN_LAB");
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(o));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OrderEntity result = service.transition(ORDER_ID, "IN_PROGRESS", "analysing");

            assertThat(result.getWorkflowState()).isEqualTo("IN_PROGRESS");
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("illegal lab transition is rejected and nothing is persisted")
    void transitionLabIllegal() {
        OrderEntity o = order(OrderType.LAB, "RECEIVED");
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.transition(ORDER_ID, "FINAL_RESULT", "skip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAB");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("imaging transition keeps imaging_state in lockstep with workflow_state")
    void transitionImagingLockstep() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            OrderEntity o = order(OrderType.IMAGING, "PERFORMED");
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(o));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OrderEntity result = service.transition(ORDER_ID, "IMAGES_LINKED", "linked");

            assertThat(result.getWorkflowState()).isEqualTo("IMAGES_LINKED");
            assertThat(result.getImagingState()).isEqualTo(ImagingWorkflowState.IMAGES_LINKED);
        }
    }

    @Test
    @DisplayName("transition on a category without a guard throws")
    void transitionNoGuard() {
        OrderEntity o = order(OrderType.PHARMACY, null);
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.transition(ORDER_ID, "ANY", "x"))
                .isInstanceOf(IllegalStateException.class);
    }
}
