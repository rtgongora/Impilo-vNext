package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderStateMachine}.
 *
 * <p>Validates the state transition graph including valid transitions,
 * invalid transitions that must be rejected, ULID generation format,
 * and order placement with items.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderStateMachineTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private OrderStateMachine stateMachine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "clinician-001";
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
    }

    private OrderEntity createOrderInStatus(OrderStatus status) {
        OrderEntity order = new OrderEntity();
        order.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-TEST-001");
        order.setOrderType(OrderType.LAB);
        order.setPriority(OrderPriority.ROUTINE);
        order.setStatus(status);
        order.setPlacedBy(ACTOR_ID);
        return order;
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, AccessMode.INTERNAL);
    }

    @Nested
    @DisplayName("Tenant isolation (IDOR guard)")
    class TenantIsolation {

        @Test
        @DisplayName("getOrder denies an order belonging to another tenant (no existence leak)")
        void getOrderDeniesCrossTenant() {
            OrderEntity order = createOrderInStatus(OrderStatus.PLACED); // tenantId = TENANT_ID
            when(orderRepository.findByOrderId(order.getOrderId())).thenReturn(java.util.Optional.of(order));
            TrustContext otherTenant = new TrustContext(java.util.UUID.randomUUID(), ACTOR_ID, "PROVIDER",
                    "TREATMENT", null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, AccessMode.INTERNAL);
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(otherTenant);
                assertThatThrownBy(() -> stateMachine.getOrder(order.getOrderId()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("not found");
            }
        }

        @Test
        @DisplayName("getOrder returns the order for its owning tenant")
        void getOrderAllowsSameTenant() {
            OrderEntity order = createOrderInStatus(OrderStatus.PLACED);
            when(orderRepository.findByOrderId(order.getOrderId())).thenReturn(java.util.Optional.of(order));
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());
                assertThat(stateMachine.getOrder(order.getOrderId())).isSameAs(order);
            }
        }
    }

    @Nested
    @DisplayName("Valid State Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("PLACED -> ACCEPTED is valid")
        void placedToAccepted() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.PLACED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.ACCEPTED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
                verify(orderRepository).save(any(OrderEntity.class));
                verify(outboxRepository).save(any(EventOutboxEntity.class));
            }
        }

        @Test
        @DisplayName("ACCEPTED -> IN_PROGRESS is valid")
        void acceptedToInProgress() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.ACCEPTED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.IN_PROGRESS);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
            }
        }

        @Test
        @DisplayName("ACCEPTED -> SCHEDULED is valid")
        void acceptedToScheduled() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.ACCEPTED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.SCHEDULED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.SCHEDULED);
            }
        }

        @Test
        @DisplayName("IN_PROGRESS -> RESULT_AVAILABLE is valid")
        void inProgressToResultAvailable() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.IN_PROGRESS);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.RESULT_AVAILABLE);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.RESULT_AVAILABLE);
            }
        }

        @Test
        @DisplayName("IN_PROGRESS -> PARTIAL_RESULT is valid")
        void inProgressToPartialResult() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.IN_PROGRESS);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.PARTIAL_RESULT);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.PARTIAL_RESULT);
            }
        }

        @Test
        @DisplayName("RESULT_AVAILABLE -> REVIEWED is valid")
        void resultAvailableToReviewed() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.RESULT_AVAILABLE);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.REVIEWED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.REVIEWED);
            }
        }

        @Test
        @DisplayName("REVIEWED -> RELEASED is valid")
        void reviewedToReleased() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.REVIEWED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.RELEASED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.RELEASED);
            }
        }

        @Test
        @DisplayName("RELEASED -> COMPLETED is valid")
        void releasedToCompleted() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.RELEASED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.COMPLETED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            }
        }

        @Test
        @DisplayName("PLACED -> CANCELLED is valid")
        void placedToCancelled() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.PLACED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.CANCELLED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            }
        }

        @Test
        @DisplayName("PLACED -> REJECTED is valid")
        void placedToRejected() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity order = createOrderInStatus(OrderStatus.PLACED);
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity result = stateMachine.transition(order, OrderStatus.REJECTED);

                assertThat(result.getStatus()).isEqualTo(OrderStatus.REJECTED);
            }
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("COMPLETED -> any state throws IllegalStateException (terminal)")
        void completedToAnything() {
            OrderEntity order = createOrderInStatus(OrderStatus.COMPLETED);

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.PLACED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid order transition");

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.IN_PROGRESS))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CANCELLED -> any state throws IllegalStateException (terminal)")
        void cancelledToAnything() {
            OrderEntity order = createOrderInStatus(OrderStatus.CANCELLED);

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.PLACED))
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.ACCEPTED))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("REJECTED -> any state throws IllegalStateException (terminal)")
        void rejectedToAnything() {
            OrderEntity order = createOrderInStatus(OrderStatus.REJECTED);

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.ACCEPTED))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("PLACED -> COMPLETED throws IllegalStateException (must go through lifecycle)")
        void placedToCompleted() {
            OrderEntity order = createOrderInStatus(OrderStatus.PLACED);

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.COMPLETED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid order transition")
                    .hasMessageContaining("PLACED")
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("IN_PROGRESS -> ACCEPTED throws IllegalStateException (cannot go backwards)")
        void inProgressToAccepted() {
            OrderEntity order = createOrderInStatus(OrderStatus.IN_PROGRESS);

            assertThatThrownBy(() -> stateMachine.transition(order, OrderStatus.ACCEPTED))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("ULID Generation")
    class UlidGeneration {

        @Test
        @DisplayName("Generated ULID is 26 characters long")
        void ulidLength() {
            String ulid = OrderStateMachine.generateUlid();
            assertThat(ulid).hasSize(26);
        }

        @Test
        @DisplayName("Generated ULIDs are unique")
        void ulidUniqueness() {
            String ulid1 = OrderStateMachine.generateUlid();
            String ulid2 = OrderStateMachine.generateUlid();
            assertThat(ulid1).isNotEqualTo(ulid2);
        }

        @Test
        @DisplayName("Generated ULID contains only valid base32 characters")
        void ulidFormat() {
            String ulid = OrderStateMachine.generateUlid();
            assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}");
        }
    }

    @Nested
    @DisplayName("Order Placement")
    class OrderPlacement {

        @Test
        @DisplayName("placeOrder creates entity with correct fields and items")
        void placeOrderCreatesEntityAndItems() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(orderItemRepository.save(any(OrderItemEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                List<OrderStateMachine.OrderItemData> items = List.of(
                        OrderStateMachine.OrderItemData.lab("CBC", "Complete Blood Count", 1, null, "BLOOD", null),
                        OrderStateMachine.OrderItemData.lab("BMP", "Basic Metabolic Panel", 1, "Fasting required", "BLOOD", null)
                );

                OrderEntity order = stateMachine.placeOrder(
                        FACILITY_ID, "CPID-001", OrderType.LAB, OrderPriority.URGENT,
                        "ZIBO-LAB-001", "ENC-123", "Patient presents with fatigue",
                        null, null, items);

                assertThat(order).isNotNull();
                assertThat(order.getOrderId()).isNotNull().hasSize(26);
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
                assertThat(order.getOrderType()).isEqualTo(OrderType.LAB);
                assertThat(order.getPriority()).isEqualTo(OrderPriority.URGENT);
                assertThat(order.getPatientCpid()).isEqualTo("CPID-001");
                assertThat(order.getFacilityId()).isEqualTo(FACILITY_ID);
                assertThat(order.getTenantId()).isEqualTo(TENANT_ID);
                assertThat(order.getPlacedBy()).isEqualTo(ACTOR_ID);
                assertThat(order.getEncounterRef()).isEqualTo("ENC-123");
                assertThat(order.getZiboOrderCode()).isEqualTo("ZIBO-LAB-001");
                assertThat(order.getClinicalNotes()).isEqualTo("Patient presents with fatigue");
                assertThat(order.getPlacedAt()).isNotNull();

                // Verify items were saved
                verify(orderItemRepository, times(2)).save(any(OrderItemEntity.class));
                // Verify outbox event
                verify(outboxRepository).save(any(EventOutboxEntity.class));
            }
        }

        @Test
        @DisplayName("placeOrder with null priority defaults to ROUTINE")
        void placeOrderDefaultPriority() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity order = stateMachine.placeOrder(
                        FACILITY_ID, "CPID-002", OrderType.IMAGING, null,
                        null, null, null, null, null, null);

                assertThat(order.getPriority()).isEqualTo(OrderPriority.ROUTINE);
            }
        }

        @Test
        @DisplayName("placeOrder generates unique order IDs across calls")
        void placeOrderUniqueIds() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity order1 = stateMachine.placeOrder(
                        FACILITY_ID, "CPID-001", OrderType.LAB, null,
                        null, null, null, null, null, null);
                OrderEntity order2 = stateMachine.placeOrder(
                        FACILITY_ID, "CPID-002", OrderType.LAB, null,
                        null, null, null, null, null, null);

                assertThat(order1.getOrderId()).isNotEqualTo(order2.getOrderId());
            }
        }

        @Test
        @DisplayName("placeOrder rejects a duplicate active TELECONSULT order (same referral + code)")
        void placeOrderRejectsDuplicateTeleconsultOrder() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity existing = new OrderEntity();
                existing.setOrderId("EXISTING");
                when(orderRepository.findByTenantIdAndSourceRefAndZiboOrderCodeAndStatusNotIn(
                        eq(TENANT_ID), eq("REF-1"), eq("ZIBO-CBC"), any()))
                        .thenReturn(java.util.List.of(existing));

                assertThatThrownBy(() -> stateMachine.placeOrder(
                        FACILITY_ID, "CPID-001", OrderType.LAB, null,
                        "ZIBO-CBC", null, null, RequestSource.TELECONSULT, "REF-1", null))
                        .isInstanceOf(zw.gov.mohcc.impilo.oros.core.OrosDomainException.class)
                        .hasMessageContaining("already exists");
                // Guard fires before persistence.
                verify(orderRepository, never()).save(any(OrderEntity.class));
            }
        }

        @Test
        @DisplayName("placeOrder allows a TELECONSULT order when no active duplicate exists")
        void placeOrderAllowsTeleconsultOrderWhenNoDuplicate() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());
                when(orderRepository.findByTenantIdAndSourceRefAndZiboOrderCodeAndStatusNotIn(
                        eq(TENANT_ID), eq("REF-1"), eq("ZIBO-CBC"), any()))
                        .thenReturn(java.util.List.of());
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                OrderEntity order = stateMachine.placeOrder(
                        FACILITY_ID, "CPID-001", OrderType.LAB, null,
                        "ZIBO-CBC", null, null, RequestSource.TELECONSULT, "REF-1", java.util.List.of());

                assertThat(order.getRequestSource()).isEqualTo(RequestSource.TELECONSULT);
                assertThat(order.getSourceRef()).isEqualTo("REF-1");
            }
        }
    }

    @Nested
    @DisplayName("Draft Lifecycle")
    class DraftLifecycle {

        @Test
        @DisplayName("createDraft creates a DRAFT imaging order with diagnostic fields and items")
        void createDraftPopulatesDiagnosticFields() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(orderItemRepository.save(any(OrderItemEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                List<OrderStateMachine.OrderItemData> items = List.of(
                        new OrderStateMachine.OrderItemData("CHEST-XR", "Chest X-Ray", 1, "PA view",
                                null, "CHEST", "XR", "BILATERAL", "NO", "PROC-71020"));

                OrderEntity order = stateMachine.createDraft(
                        FACILITY_ID, "CPID-IMG-001", OrderType.IMAGING, OrderPriority.ROUTINE,
                        RequestSource.INTERNAL, null, "ZIBO-XR", "ENC-1", "Cough 2 weeks",
                        "prov-1", "Dr Referrer", null, "{\"pregnant\":false}", items);

                assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
                assertThat(order.getOrderType()).isEqualTo(OrderType.IMAGING);
                assertThat(order.getRequestSource()).isEqualTo(RequestSource.INTERNAL);
                assertThat(order.getReferringProviderId()).isEqualTo("prov-1");
                assertThat(order.getReferringProviderName()).isEqualTo("Dr Referrer");
                assertThat(order.getSafetyJson()).contains("pregnant");
                verify(orderItemRepository).save(any(OrderItemEntity.class));
                verify(outboxRepository).save(any(EventOutboxEntity.class));
            }
        }

        @Test
        @DisplayName("updateDraft on a DRAFT order replaces items and updates fields")
        void updateDraftReplacesItems() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity existing = createOrderInStatus(OrderStatus.DRAFT);
                existing.setOrderType(OrderType.IMAGING);
                when(orderRepository.findByOrderId(existing.getOrderId())).thenReturn(Optional.of(existing));
                when(orderRepository.save(any(OrderEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(orderItemRepository.save(any(OrderItemEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                List<OrderStateMachine.OrderItemData> items = List.of(
                        OrderStateMachine.OrderItemData.lab("US-ABD", "Abdominal US", 1, null, null, "ABDOMEN"));

                OrderEntity updated = stateMachine.updateDraft(
                        existing.getOrderId(), OrderPriority.URGENT, RequestSource.PAPER,
                        "ZIBO-2", "ENC-2", "Updated notes", "prov-2", "Dr Two", null, null, items);

                assertThat(updated.getPriority()).isEqualTo(OrderPriority.URGENT);
                assertThat(updated.getRequestSource()).isEqualTo(RequestSource.PAPER);
                assertThat(updated.getClinicalNotes()).isEqualTo("Updated notes");
                verify(orderItemRepository).deleteByOrderId(existing.getOrderId());
                verify(orderItemRepository).save(any(OrderItemEntity.class));
            }
        }

        @Test
        @DisplayName("updateDraft on a non-DRAFT order is rejected")
        void updateDraftRejectsNonDraft() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                OrderEntity placed = createOrderInStatus(OrderStatus.PLACED);
                when(orderRepository.findByOrderId(placed.getOrderId())).thenReturn(Optional.of(placed));

                assertThatThrownBy(() -> stateMachine.updateDraft(
                        placed.getOrderId(), null, null, null, null, null, null, null, null, null, null))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("not a draft");
            }
        }
    }
}
