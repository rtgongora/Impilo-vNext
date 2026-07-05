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
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ImagingWorkflowService} — the single owner of fine-grained imaging
 * workflow state transitions.
 */
@ExtendWith(MockitoExtension.class)
class ImagingWorkflowServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository orderItemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private zw.gov.mohcc.impilo.oros.integration.ButanoIntegration butanoIntegration;

    private ImagingWorkflowService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        service = new ImagingWorkflowService(
                orderRepository, orderItemRepository, outboxRepository, objectMapper, butanoIntegration);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "radiographer-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private OrderEntity imagingOrder(ImagingWorkflowState state) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(ORDER_ID);
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-IMG-001");
        order.setOrderType(OrderType.IMAGING);
        order.setImagingState(state);
        return order;
    }

    @Test
    @DisplayName("initializeReceived sets RECEIVED, writes event, does not persist")
    void initializeReceivedSetsEntryState() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = imagingOrder(null);
            service.initializeReceived(order, "submitted");

            assertThat(order.getImagingState()).isEqualTo(ImagingWorkflowState.RECEIVED);
            verify(outboxRepository).save(any(EventOutboxEntity.class));
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("initializeReceived is idempotent when a state already exists")
    void initializeReceivedIdempotent() {
        OrderEntity order = imagingOrder(ImagingWorkflowState.ACCEPTED);
        service.initializeReceived(order, "submitted");

        assertThat(order.getImagingState()).isEqualTo(ImagingWorkflowState.ACCEPTED);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeReceived rejects non-imaging orders")
    void initializeReceivedRejectsNonImaging() {
        OrderEntity order = imagingOrder(null);
        order.setOrderType(OrderType.LAB);

        assertThatThrownBy(() -> service.initializeReceived(order, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IMAGING");
    }

    @Test
    @DisplayName("transition RECEIVED -> ACCEPTED persists and emits IMAGING_STATE_ACCEPTED")
    void transitionValid() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = imagingOrder(ImagingWorkflowState.RECEIVED);
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OrderEntity result = service.transition(ORDER_ID, ImagingWorkflowState.ACCEPTED, "ok");

            assertThat(result.getImagingState()).isEqualTo(ImagingWorkflowState.ACCEPTED);
            verify(orderRepository).save(any(OrderEntity.class));
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("transition rejects an illegal skip (RECEIVED -> RELEASED)")
    void transitionIllegalSkip() {
        OrderEntity order = imagingOrder(ImagingWorkflowState.RECEIVED);
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.transition(ORDER_ID, ImagingWorkflowState.RELEASED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid imaging workflow transition");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("linkStudy records study UID/viewer and drives state to IMAGES_LINKED")
    void linkStudySetsStudyAndState() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = imagingOrder(ImagingWorkflowState.PERFORMED);
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OrderEntity result = service.linkStudy(ORDER_ID, "1.2.840.113619.2", "https://viewer/launch?s=1", "ACC-9");

            assertThat(result.getImagingState()).isEqualTo(ImagingWorkflowState.IMAGES_LINKED);
            assertThat(result.getStudyUid()).isEqualTo("1.2.840.113619.2");
            assertThat(result.getStudyViewerUrl()).isEqualTo("https://viewer/launch?s=1");
            assertThat(result.getAccessionNumber()).isEqualTo("ACC-9");
            // FHIR ImagingStudy writeback attempted on link (flag-gated inside the integration).
            verify(butanoIntegration).createImagingStudy(any(OrderEntity.class), any());
        }
    }

    @Test
    @DisplayName("linkStudy is rejected before the exam is performed")
    void linkStudyRejectedBeforePerformed() {
        OrderEntity order = imagingOrder(ImagingWorkflowState.RECEIVED);
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.linkStudy(ORDER_ID, "1.2.3", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid imaging workflow transition");
    }

    @Test
    @DisplayName("schedule sets scheduledAt and drives state to SCHEDULED")
    void scheduleSetsTimeAndState() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());

            OrderEntity order = imagingOrder(ImagingWorkflowState.ACCEPTED);
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));

            OffsetDateTime when = OffsetDateTime.now().plusDays(1);
            OrderEntity result = service.schedule(ORDER_ID, when, "booked");

            assertThat(result.getImagingState()).isEqualTo(ImagingWorkflowState.SCHEDULED);
            assertThat(result.getScheduledAt()).isEqualTo(when);
        }
    }

    @Test
    @DisplayName("firstItemModality resolves the first item modality, normalized to upper case")
    void firstItemModalityResolvesFromItems() {
        OrderEntity order = imagingOrder(ImagingWorkflowState.SCHEDULED);
        var itemNoModality = new zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity();
        var itemCt = new zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity();
        itemCt.setModality(" ct ");
        when(orderItemRepository.findByOrderId(ORDER_ID))
                .thenReturn(java.util.List.of(itemNoModality, itemCt));

        assertThat(service.firstItemModality(order)).isEqualTo("CT");
    }

    @Test
    @DisplayName("firstItemModality returns null (never guesses) when no item declares a modality")
    void firstItemModalityNullWhenAbsent() {
        OrderEntity order = imagingOrder(ImagingWorkflowState.SCHEDULED);
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(java.util.List.of());

        assertThat(service.firstItemModality(order)).isNull();
    }
}
