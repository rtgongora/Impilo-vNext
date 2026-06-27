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
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.SpecimenStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SpecimenEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.SpecimenRepository;
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
 * Unit tests for {@link SpecimenService} — the laboratory specimen lifecycle and the order-workflow
 * advancement it drives.
 */
@ExtendWith(MockitoExtension.class)
class SpecimenServiceTest {

    @Mock private SpecimenRepository specimenRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private FulfilmentWorkflowService fulfilmentWorkflowService;
    @Mock private EventOutboxRepository outboxRepository;

    private SpecimenService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper om = OrosTestObjectMapper.create();
        WorkflowGuardRegistry guards = new WorkflowGuardRegistry(List.of(
                new ImagingFulfilmentWorkflow(), new LabWorkflow(), new ProcedureWorkflow()));
        service = new SpecimenService(specimenRepository, orderRepository,
                fulfilmentWorkflowService, guards, outboxRepository, om);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "phlebotomist-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private OrderEntity labOrder(String workflowState) {
        OrderEntity o = new OrderEntity();
        o.setOrderId(ORDER_ID);
        o.setTenantId(TENANT_ID);
        o.setFacilityId(FACILITY_ID);
        o.setOrderType(OrderType.LAB);
        o.setWorkflowState(workflowState);
        return o;
    }

    private void stubSaveReturnsWithId() {
        when(specimenRepository.save(any(SpecimenEntity.class))).thenAnswer(i -> {
            SpecimenEntity s = i.getArgument(0);
            if (s.getSpecimenId() == null) s.setSpecimenId(UUID.randomUUID());
            return s;
        });
    }

    @Test
    @DisplayName("collect creates a COLLECTED specimen, generates a sample id, advances the order, emits an event")
    void collect() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(labOrder("RECEIVED")));
            stubSaveReturnsWithId();

            SpecimenEntity s = service.collect(ORDER_ID, "blood", "venous", "left arm",
                    "FASTING", "OPD phleb", null, null);

            assertThat(s.getStatus()).isEqualTo(SpecimenStatus.COLLECTED);
            assertThat(s.getSampleId()).startsWith("SPC-");
            assertThat(s.getCollectedBy()).isEqualTo("phlebotomist-1");
            assertThat(s.getChainOfCustody()).contains("COLLECTED");
            // RECEIVED -> COLLECTED is a legal LAB transition, so the order is advanced.
            verify(fulfilmentWorkflowService).transition(eq(ORDER_ID), eq("COLLECTED"), any());
            ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(ev.capture());
            assertThat(ev.getValue().getEventType()).isEqualTo("SPECIMEN_COLLECTED");
        }
    }

    @Test
    @DisplayName("receive sets the lab number and advances the order to RECEIVED_IN_LAB")
    void receive() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            SpecimenEntity existing = new SpecimenEntity();
            existing.setSpecimenId(UUID.randomUUID());
            existing.setOrderId(ORDER_ID);
            existing.setTenantId(TENANT_ID);
            existing.setStatus(SpecimenStatus.DISPATCHED);
            when(specimenRepository.findById(existing.getSpecimenId())).thenReturn(Optional.of(existing));
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(labOrder("DISPATCHED")));
            stubSaveReturnsWithId();

            SpecimenEntity s = service.receive(existing.getSpecimenId(), "LAB-2026-0042");

            assertThat(s.getStatus()).isEqualTo(SpecimenStatus.RECEIVED);
            assertThat(s.getLabNumber()).isEqualTo("LAB-2026-0042");
            verify(fulfilmentWorkflowService).transition(eq(ORDER_ID), eq("RECEIVED_IN_LAB"), any());
        }
    }

    @Test
    @DisplayName("reject records the reason and advances the order to SPECIMEN_REJECTED")
    void reject() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            SpecimenEntity existing = new SpecimenEntity();
            existing.setSpecimenId(UUID.randomUUID());
            existing.setOrderId(ORDER_ID);
            existing.setTenantId(TENANT_ID);
            existing.setStatus(SpecimenStatus.RECEIVED);
            when(specimenRepository.findById(existing.getSpecimenId())).thenReturn(Optional.of(existing));
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(labOrder("RECEIVED_IN_LAB")));
            stubSaveReturnsWithId();

            SpecimenEntity s = service.reject(existing.getSpecimenId(), "haemolysed");

            assertThat(s.getStatus()).isEqualTo(SpecimenStatus.REJECTED);
            assertThat(s.getRejectionReason()).isEqualTo("haemolysed");
            verify(fulfilmentWorkflowService).transition(eq(ORDER_ID), eq("SPECIMEN_REJECTED"), any());
        }
    }

    @Test
    @DisplayName("order advance is skipped when the current workflow state forbids the transition")
    void advanceSkippedWhenIllegal() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            // CLOSED is terminal — collecting a specimen cannot move it.
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(labOrder("CLOSED")));
            stubSaveReturnsWithId();

            service.collect(ORDER_ID, "urine", null, null, null, null, null, "SPC-MANUAL-1");

            verify(fulfilmentWorkflowService, never()).transition(any(), any(), any());
        }
    }
}
