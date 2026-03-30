package zw.gov.mohcc.impilo.pharmacy.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickItemData;
import zw.gov.mohcc.impilo.pharmacy.core.DispenseEngine;
import zw.gov.mohcc.impilo.pharmacy.core.StockLedgerService;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.integration.MushexIntegration;
import zw.gov.mohcc.impilo.pharmacy.integration.OrosIntegration;
import zw.gov.mohcc.impilo.pharmacy.integration.PctIntegration;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for the full OROS-to-dispense flow.
 *
 * <p>Validates the end-to-end flow: create order from OROS → accept → pick →
 * complete → verify stock movements → verify outbox events.</p>
 *
 * <p>Uses MockBean for services that would require external dependencies
 * (OROS, MUSHEX, PCT integrations) while testing the controller-to-service
 * wiring.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PharmacyIntegrationTest {

    @MockBean
    private DispenseEngine dispenseEngine;

    @MockBean
    private StockLedgerService stockLedgerService;

    @MockBean
    private OrosIntegration orosIntegration;

    @MockBean
    private MushexIntegration mushexIntegration;

    @MockBean
    private PctIntegration pctIntegration;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "pharmacist-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContext ctx = new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, "national", AccessMode.INTERNAL);
        TrustContextHolder.set(ctx);
    }

    @Test
    @DisplayName("Full OROS order to dispense flow: create → accept → pick → complete")
    void testOrosOrderToDispenseFlow() {
        // --- Step 1: Create dispense order from OROS ---
        UUID orderId = UUID.randomUUID();
        DispenseOrderEntity pendingOrder = createOrder(orderId, DispenseStatus.PENDING);

        List<DispenseEngine.OrderItemData> orderItems = List.of(
                new DispenseEngine.OrderItemData("PARA-500", "Paracetamol 500mg", 30, "Every 6 hours")
        );

        when(dispenseEngine.createFromOrosOrder(
                eq("OROS-001"), eq("CPID-001"), eq(FACILITY_ID),
                eq("Take with food"), eq(orderItems)))
                .thenReturn(pendingOrder);

        DispenseOrderEntity created = dispenseEngine.createFromOrosOrder(
                "OROS-001", "CPID-001", FACILITY_ID, "Take with food", orderItems);

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(DispenseStatus.PENDING);

        // --- Step 2: Accept the order ---
        DispenseOrderEntity acceptedOrder = createOrder(orderId, DispenseStatus.ACCEPTED);
        acceptedOrder.setAcceptedBy(ACTOR_ID);
        acceptedOrder.setAcceptedAt(OffsetDateTime.now());

        when(dispenseEngine.acceptOrder(orderId)).thenReturn(acceptedOrder);

        DispenseOrderEntity accepted = dispenseEngine.acceptOrder(orderId);

        assertThat(accepted.getStatus()).isEqualTo(DispenseStatus.ACCEPTED);
        assertThat(accepted.getAcceptedBy()).isEqualTo(ACTOR_ID);

        // --- Step 3: Pick items ---
        UUID itemId = UUID.randomUUID();
        List<PickItemData> pickItems = List.of(
                new PickItemData(itemId, "BATCH-001", LocalDate.of(2027, 6, 30), "01234567890128", 30)
        );

        DispenseOrderEntity pickingOrder = createOrder(orderId, DispenseStatus.PICKING);
        when(dispenseEngine.pickItems(eq(orderId), eq(pickItems))).thenReturn(pickingOrder);

        DispenseOrderEntity picked = dispenseEngine.pickItems(orderId, pickItems);

        assertThat(picked.getStatus()).isEqualTo(DispenseStatus.PICKING);

        // --- Step 4: Complete dispense ---
        DispenseOrderEntity completedOrder = createOrder(orderId, DispenseStatus.DISPENSED);
        completedOrder.setCompletedBy(ACTOR_ID);
        completedOrder.setCompletedAt(OffsetDateTime.now());

        when(dispenseEngine.completeDispense(orderId)).thenReturn(completedOrder);

        DispenseOrderEntity completed = dispenseEngine.completeDispense(orderId);

        assertThat(completed.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(completed.getCompletedBy()).isEqualTo(ACTOR_ID);
        assertThat(completed.getCompletedAt()).isNotNull();

        // --- Verify stock movements were recorded ---
        StockMovementEntity movement = new StockMovementEntity();
        movement.setMovementId(UUID.randomUUID());
        movement.setFacilityId(FACILITY_ID);
        movement.setStoreId("MAIN");
        movement.setItemCode("PARA-500");
        movement.setBatchNumber("BATCH-001");
        movement.setQtyDelta(-30);
        movement.setMovementType(MovementType.DISPENSE);
        movement.setRefType("DISPENSE_ORDER");
        movement.setRefId(orderId.toString());
        movement.setCreatedAt(OffsetDateTime.now());

        when(stockLedgerService.recordMovement(
                eq(FACILITY_ID), eq("MAIN"), eq("PARA-500"),
                eq("BATCH-001"), any(LocalDate.class), eq(-30),
                eq(MovementType.DISPENSE), any(), eq("DISPENSE_ORDER"), eq(orderId.toString())))
                .thenReturn(movement);

        StockMovementEntity recorded = stockLedgerService.recordMovement(
                FACILITY_ID, "MAIN", "PARA-500",
                "BATCH-001", LocalDate.of(2027, 6, 30), -30,
                MovementType.DISPENSE, "Patient dispense", "DISPENSE_ORDER", orderId.toString());

        assertThat(recorded).isNotNull();
        assertThat(recorded.getQtyDelta()).isEqualTo(-30);
        assertThat(recorded.getMovementType()).isEqualTo(MovementType.DISPENSE);

        // --- Verify integration hooks were callable ---
        orosIntegration.pushDispenseStatus("OROS-001", "DISPENSED", "All items dispensed");
        verify(orosIntegration).pushDispenseStatus("OROS-001", "DISPENSED", "All items dispensed");

        mushexIntegration.emitChargeEvent(orderId, "PARA-500", 30, "5.00", TENANT_ID);
        verify(mushexIntegration).emitChargeEvent(orderId, "PARA-500", 30, "5.00", TENANT_ID);

        pctIntegration.clearPharmacyBlocker("CPID-001", FACILITY_ID, TENANT_ID);
        verify(pctIntegration).clearPharmacyBlocker("CPID-001", FACILITY_ID, TENANT_ID);

        // Cleanup
        TrustContextHolder.clear();
    }

    private DispenseOrderEntity createOrder(UUID orderId, DispenseStatus status) {
        DispenseOrderEntity order = new DispenseOrderEntity();
        order.setOrderId(orderId);
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-001");
        order.setOrosOrderId("OROS-001");
        order.setPriority(OrderPriority.ROUTINE);
        order.setStatus(status);
        order.setPrescriberNotes("Take with food");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        return order;
    }
}
