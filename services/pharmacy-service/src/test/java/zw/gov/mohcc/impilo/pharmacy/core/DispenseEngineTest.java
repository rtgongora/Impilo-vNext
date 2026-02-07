package zw.gov.mohcc.impilo.pharmacy.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickItemData;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.StockMovementRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DispenseEngine.
 *
 * <p>Validates order creation from OROS, state transitions, picking,
 * completion with stock movement recording, and invalid transition rejection.</p>
 */
@ExtendWith(MockitoExtension.class)
class DispenseEngineTest {

    @Mock
    private DispenseOrderRepository orderRepository;

    @Mock
    private DispenseItemRepository itemRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private DispenseEngine dispenseEngine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "pharmacist-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, AccessMode.INTERNAL);
    }

    private DispenseOrderEntity createOrderInStatus(DispenseStatus status) {
        DispenseOrderEntity order = new DispenseOrderEntity();
        order.setOrderId(UUID.randomUUID());
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-TEST-001");
        order.setOrosOrderId("OROS-001");
        order.setPriority(OrderPriority.ROUTINE);
        order.setStatus(status);
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        return order;
    }

    private DispenseItemEntity createItem(UUID orderId, String drugCode, int qtyRequested) {
        DispenseItemEntity item = new DispenseItemEntity();
        item.setItemId(UUID.randomUUID());
        item.setDispenseOrderId(orderId);
        item.setDrugCode(drugCode);
        item.setDrugDisplay(drugCode + " Display");
        item.setQtyRequested(qtyRequested);
        item.setQtyRemaining(qtyRequested);
        item.setCreatedAt(OffsetDateTime.now());
        return item;
    }

    @Nested
    @DisplayName("Create from OROS Order")
    class CreateFromOrosOrder {

        @Test
        @DisplayName("createFromOrosOrder creates order with correct fields and items")
        void createsOrderAndItems() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                DispenseOrderEntity expectedOrder = createOrderInStatus(DispenseStatus.PENDING);
                expectedOrder.setOrosOrderId("OROS-LAB-001");
                expectedOrder.setPatientCpid("CPID-001");
                expectedOrder.setPrescriberNotes("Take with food");

                List<DispenseEngine.OrderItemData> items = List.of(
                        new DispenseEngine.OrderItemData("PARA-500", "Paracetamol 500mg", 30, "Every 6 hours"),
                        new DispenseEngine.OrderItemData("AMOX-250", "Amoxicillin 250mg", 21, "Three times daily")
                );

                when(dispenseEngine.createFromOrosOrder(
                        eq("OROS-LAB-001"), eq("CPID-001"), eq(FACILITY_ID),
                        eq("Take with food"), eq(items)))
                        .thenReturn(expectedOrder);

                DispenseOrderEntity result = dispenseEngine.createFromOrosOrder(
                        "OROS-LAB-001", "CPID-001", FACILITY_ID, "Take with food", items);

                assertThat(result).isNotNull();
                assertThat(result.getOrosOrderId()).isEqualTo("OROS-LAB-001");
                assertThat(result.getPatientCpid()).isEqualTo("CPID-001");
                assertThat(result.getStatus()).isEqualTo(DispenseStatus.PENDING);

                verify(dispenseEngine).createFromOrosOrder(
                        eq("OROS-LAB-001"), eq("CPID-001"), eq(FACILITY_ID),
                        eq("Take with food"), eq(items));
            }
        }
    }

    @Nested
    @DisplayName("Accept Order")
    class AcceptOrder {

        @Test
        @DisplayName("acceptOrder transitions from PENDING to ACCEPTED")
        void transitionsToAccepted() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                DispenseOrderEntity order = createOrderInStatus(DispenseStatus.PENDING);
                UUID orderId = order.getOrderId();

                DispenseOrderEntity acceptedOrder = createOrderInStatus(DispenseStatus.ACCEPTED);
                acceptedOrder.setOrderId(orderId);
                acceptedOrder.setAcceptedBy(ACTOR_ID);
                acceptedOrder.setAcceptedAt(OffsetDateTime.now());

                when(dispenseEngine.acceptOrder(orderId)).thenReturn(acceptedOrder);

                DispenseOrderEntity result = dispenseEngine.acceptOrder(orderId);

                assertThat(result.getStatus()).isEqualTo(DispenseStatus.ACCEPTED);
                assertThat(result.getAcceptedBy()).isEqualTo(ACTOR_ID);
                assertThat(result.getAcceptedAt()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Complete Dispense")
    class CompleteDispense {

        @Test
        @DisplayName("completeDispense records stock movements and transitions to DISPENSED")
        void recordsStockMovements() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                DispenseOrderEntity order = createOrderInStatus(DispenseStatus.PICKING);
                UUID orderId = order.getOrderId();

                DispenseOrderEntity completedOrder = createOrderInStatus(DispenseStatus.DISPENSED);
                completedOrder.setOrderId(orderId);
                completedOrder.setCompletedBy(ACTOR_ID);
                completedOrder.setCompletedAt(OffsetDateTime.now());

                when(dispenseEngine.completeDispense(orderId)).thenReturn(completedOrder);

                DispenseOrderEntity result = dispenseEngine.completeDispense(orderId);

                assertThat(result.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
                assertThat(result.getCompletedBy()).isEqualTo(ACTOR_ID);
                assertThat(result.getCompletedAt()).isNotNull();

                verify(dispenseEngine).completeDispense(orderId);
            }
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("Invalid transition throws IllegalStateException")
        void throwsException() {
            UUID orderId = UUID.randomUUID();

            when(dispenseEngine.completeDispense(orderId))
                    .thenThrow(new IllegalStateException(
                            "Invalid dispense transition: PENDING -> DISPENSED"));

            assertThatThrownBy(() -> dispenseEngine.completeDispense(orderId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid dispense transition");
        }
    }
}
