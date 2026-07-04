package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.integration.VarapiClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderSubmissionService} — the draft→submit promotion with accession
 * reservation, VARAPI provider resolution, and imaging workflow initialization.
 */
@ExtendWith(MockitoExtension.class)
class OrderSubmissionServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private AccessionNumberService accessionNumberService;
    @Mock private VarapiClient varapiClient;

    private OrderSubmissionService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        OrderStateMachine stateMachine = new OrderStateMachine(
                orderRepository, orderItemRepository, outboxRepository, objectMapper);
        ImagingWorkflowService imagingWorkflowService = new ImagingWorkflowService(
                orderRepository, orderItemRepository, outboxRepository, objectMapper,
                org.mockito.Mockito.mock(zw.gov.mohcc.impilo.oros.integration.ButanoIntegration.class));
        zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry guards =
                new zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry(java.util.List.of(
                        new zw.gov.mohcc.impilo.oros.core.workflow.ImagingFulfilmentWorkflow(),
                        new zw.gov.mohcc.impilo.oros.core.workflow.LabWorkflow(),
                        new zw.gov.mohcc.impilo.oros.core.workflow.ProcedureWorkflow()));
        FulfilmentWorkflowService fulfilmentWorkflowService = new FulfilmentWorkflowService(
                orderRepository, outboxRepository, objectMapper, guards);
        service = new OrderSubmissionService(
                stateMachine, accessionNumberService, varapiClient,
                imagingWorkflowService, fulfilmentWorkflowService);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private OrderEntity draft(OrderType type) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(ORDER_ID);
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-001");
        order.setOrderType(type);
        order.setStatus(OrderStatus.DRAFT);
        order.setPlacedBy("clinician-1");
        return order;
    }

    @Test
    @DisplayName("submit of an imaging draft reserves accession, resolves provider, inits RECEIVED, PLACED")
    void submitImaging() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = draft(OrderType.IMAGING);
            order.setReferringProviderId("prov-1");
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(accessionNumberService.reserve(TENANT_ID, FACILITY_ID)).thenReturn("ACC-2026-AB12CD34-000001");
            when(varapiClient.lookupProviderName("prov-1")).thenReturn(Optional.of("Dr Jane Doe"));

            OrderEntity result = service.submit(ORDER_ID);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(result.getAccessionNumber()).isEqualTo("ACC-2026-AB12CD34-000001");
            assertThat(result.getReferringProviderName()).isEqualTo("Dr Jane Doe");
            assertThat(result.getImagingState()).isEqualTo(ImagingWorkflowState.RECEIVED);
            assertThat(result.getWorkflowState()).isEqualTo("RECEIVED");
            verify(accessionNumberService, times(1)).reserve(TENANT_ID, FACILITY_ID);
        }
    }

    @Test
    @DisplayName("submit does not re-reserve an accession that is already present")
    void submitImagingIdempotentAccession() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = draft(OrderType.IMAGING);
            order.setAccessionNumber("ACC-2026-AB12CD34-000099");
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OrderEntity result = service.submit(ORDER_ID);

            assertThat(result.getAccessionNumber()).isEqualTo("ACC-2026-AB12CD34-000099");
            verify(accessionNumberService, never()).reserve(any(), any());
        }
    }

    @Test
    @DisplayName("VARAPI not resolving keeps the client-supplied referring provider name")
    void submitImagingVarapiFallback() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = draft(OrderType.IMAGING);
            order.setReferringProviderId("prov-x");
            order.setReferringProviderName("Client Supplied Name");
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(accessionNumberService.reserve(TENANT_ID, FACILITY_ID)).thenReturn("ACC-2026-AB12CD34-000002");
            when(varapiClient.lookupProviderName("prov-x")).thenReturn(Optional.empty());

            OrderEntity result = service.submit(ORDER_ID);

            assertThat(result.getReferringProviderName()).isEqualTo("Client Supplied Name");
        }
    }

    @Test
    @DisplayName("submit of a lab draft reserves a lab number, inits the lab workflow at RECEIVED, PLACED")
    void submitLab() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = draft(OrderType.LAB);
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(accessionNumberService.reserve(TENANT_ID, FACILITY_ID)).thenReturn("ACC-2026-AB12CD34-000007");

            OrderEntity result = service.submit(ORDER_ID);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
            // No imaging projection for a lab order, but the unified workflow state is set.
            assertThat(result.getImagingState()).isNull();
            assertThat(result.getWorkflowState()).isEqualTo("RECEIVED");
            assertThat(result.getAccessionNumber()).isEqualTo("ACC-2026-AB12CD34-000007");
            verify(accessionNumberService, times(1)).reserve(TENANT_ID, FACILITY_ID);
            verify(varapiClient, never()).lookupProviderName(any());
        }
    }

    @Test
    @DisplayName("submit of a procedure draft inits the procedure workflow at RECEIVED without an accession")
    void submitProcedure() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = draft(OrderType.PROCEDURE);
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OrderEntity result = service.submit(ORDER_ID);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(result.getWorkflowState()).isEqualTo("RECEIVED");
            assertThat(result.getImagingState()).isNull();
            verify(accessionNumberService, never()).reserve(any(), any());
        }
    }

    @Test
    @DisplayName("submit of a non-draft order is rejected")
    void submitNonDraftRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = draft(OrderType.IMAGING);
            order.setStatus(OrderStatus.PLACED);
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.submit(ORDER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be submitted");
        }
    }
}
