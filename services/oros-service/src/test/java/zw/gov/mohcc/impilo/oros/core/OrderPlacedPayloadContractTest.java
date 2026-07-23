package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
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
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderVersionEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderVersionRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * M3 BUG-3 + BUG-2 (OROS side).
 *
 * <p>BUG-3 — the {@code oros.order.placed} payload used to be the bare
 * serialized OrderEntity with NO items; the pharmacy consumer's documented
 * contract expects {@code items[]}, so consumer-spawned dispense episodes had
 * zero items. The fix is ADDITIVE: every pre-existing OrderEntity field name
 * must survive verbatim, PLUS the items array.</p>
 *
 * <p>BUG-2 — the narrow external-refs producer endpoint: allowlisted key,
 * merge-into-jsonb, idempotent replay, conflict on a different value.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderPlacedPayloadContractTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private OrderVersionRepository versionRepository;

    private ObjectMapper objectMapper;
    private OrderStateMachine stateMachine;
    private OrderLifecycleService lifecycleService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = OrosTestObjectMapper.create();
        OrderVersionWriter versionWriter =
                new OrderVersionWriter(versionRepository, orderItemRepository, objectMapper);
        stateMachine = new OrderStateMachine(
                orderRepository, orderItemRepository, outboxRepository, objectMapper, versionWriter);
        lifecycleService = new OrderLifecycleService(stateMachine, versionWriter, orderRepository,
                orderItemRepository, versionRepository, objectMapper);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-001", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private static OrderItemEntity item(String orderId, String code, String display,
                                        int qty, String instructions) {
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderId(orderId);
        item.setCode(code);
        item.setDisplayName(display);
        item.setQuantity(qty);
        item.setInstructions(instructions);
        return item;
    }

    @Nested
    @DisplayName("BUG-3: ORDER_PLACED payload carries items, existing field names intact")
    class PlacedPayload {

        @Test
        void placeOrder_emitsOrderPlacedWithItemsArray() throws Exception {
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(orderItemRepository.save(any(OrderItemEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            lenient().when(versionRepository.save(any(OrderVersionEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            lenient().when(versionRepository.findFirstByOrderIdOrderByVersionDesc(anyString()))
                    .thenReturn(Optional.empty());
            when(orderItemRepository.findByOrderId(anyString())).thenAnswer(inv ->
                    List.of(item(inv.getArgument(0), "C09AA05", "Ramipril 5mg", 10, "1 OD")));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                stateMachine.placeOrder(FACILITY_ID, "CPID-1", OrderType.PHARMACY,
                        OrderPriority.ROUTINE, null, null, null, RequestSource.INTERNAL, null,
                        List.of(OrderStateMachine.OrderItemData.lab(
                                "C09AA05", "Ramipril 5mg", 10, "1 OD", null, null)));
            }

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            EventOutboxEntity event = captor.getValue();
            assertThat(event.getEventType()).isEqualTo("ORDER_PLACED");

            JsonNode payload = objectMapper.readTree(event.getPayload());
            // Pre-existing OrderEntity field names preserved verbatim:
            assertThat(payload.path("orderId").asText()).isNotBlank();
            assertThat(payload.path("tenantId").asText()).isEqualTo(TENANT_ID.toString());
            assertThat(payload.path("facilityId").asText()).isEqualTo(FACILITY_ID.toString());
            assertThat(payload.path("patientCpid").asText()).isEqualTo("CPID-1");
            assertThat(payload.path("orderType").asText()).isEqualTo("PHARMACY");
            assertThat(payload.path("status").asText()).isEqualTo("PLACED");
            // The BUG-3 addition — the items array the pharmacy consumer expects:
            JsonNode items = payload.path("items");
            assertThat(items.isArray()).isTrue();
            assertThat(items).hasSize(1);
            assertThat(items.get(0).path("code").asText()).isEqualTo("C09AA05");
            assertThat(items.get(0).path("displayName").asText()).isEqualTo("Ramipril 5mg");
            assertThat(items.get(0).path("quantity").asInt()).isEqualTo(10);
            assertThat(items.get(0).path("instructions").asText()).isEqualTo("1 OD");
        }

        @Test
        void transitionToPlaced_alsoCarriesItems() throws Exception {
            OrderEntity order = new OrderEntity();
            order.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
            order.setTenantId(TENANT_ID);
            order.setFacilityId(FACILITY_ID);
            order.setPatientCpid("CPID-2");
            order.setOrderType(OrderType.PHARMACY);
            order.setPriority(OrderPriority.ROUTINE);
            order.setStatus(OrderStatus.PENDING_SIGNATURE);
            order.setRequestSource(RequestSource.INTERNAL);

            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(versionRepository.save(any(OrderVersionEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            lenient().when(versionRepository.findFirstByOrderIdOrderByVersionDesc(anyString()))
                    .thenReturn(Optional.empty());
            lenient().when(versionRepository.existsByOrderId(anyString())).thenReturn(false);
            when(orderItemRepository.findByOrderId(order.getOrderId())).thenReturn(
                    List.of(item(order.getOrderId(), "B01AC06", "Aspirin", 5, null)));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                stateMachine.transition(order, OrderStatus.PLACED);
            }

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
            assertThat(captor.getValue().getEventType()).isEqualTo("ORDER_PLACED");
            assertThat(payload.path("items")).hasSize(1);
            assertThat(payload.path("items").get(0).path("code").asText()).isEqualTo("B01AC06");
        }
    }

    @Nested
    @DisplayName("BUG-2: narrow external-refs producer endpoint semantics")
    class ExternalRefs {

        private OrderEntity placedOrder() {
            OrderEntity order = new OrderEntity();
            order.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
            order.setTenantId(TENANT_ID);
            order.setFacilityId(FACILITY_ID);
            order.setPatientCpid("CPID-3");
            order.setOrderType(OrderType.PHARMACY);
            order.setStatus(OrderStatus.PLACED);
            return order;
        }

        @Test
        void recordsAllowlistedKey_mergesAndEmitsEvent() {
            OrderEntity order = placedOrder();
            order.setExternalRefs("{\"replaces\":\"01OLDORDERXXXXXXXXXXXXXXXX\"}");
            when(orderRepository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                lifecycleService.recordExternalRef(order.getOrderId(), "prescriptionClaimId", "claim-123");

                // merge — the existing T7 link survives
                Map<String, String> refs = lifecycleService.externalRefs(order.getOrderId());
                assertThat(refs)
                        .containsEntry("prescriptionClaimId", "claim-123")
                        .containsEntry("replaces", "01OLDORDERXXXXXXXXXXXXXXXX");
            }
            verify(outboxRepository).save(argThat(e ->
                    "ORDER_EXTERNAL_REF_RECORDED".equals(e.getEventType())));
        }

        @Test
        void idempotentReplay_isANoOp_andDifferentValueConflicts() {
            OrderEntity order = placedOrder();
            order.setExternalRefs("{\"prescriptionClaimId\":\"claim-123\"}");
            when(orderRepository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                // replay same value → no-op, no save, no event
                lifecycleService.recordExternalRef(order.getOrderId(), "prescriptionClaimId", "claim-123");
                verify(orderRepository, never()).save(any());
                verify(outboxRepository, never()).save(any());

                // different value → 409 conflict, never a silent overwrite
                assertThatThrownBy(() -> lifecycleService.recordExternalRef(
                        order.getOrderId(), "prescriptionClaimId", "claim-999"))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("different value");
            }
        }

        @Test
        void nonAllowlistedKey_isRefused() {
            OrderEntity order = placedOrder();
            when(orderRepository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                // replaces/replacedBy are lifecycle-owned — never caller-writable
                assertThatThrownBy(() -> lifecycleService.recordExternalRef(
                        order.getOrderId(), "replacedBy", "01EVILORDERXXXXXXXXXXXXXXX"))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("not allowed");
            }
        }
    }
}
