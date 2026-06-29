package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.SupplierOrderStatus;
import zw.gov.mohcc.impilo.inventory.domain.SupplierType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierProfileEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.SupplierCatalogueItemRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.SupplierOrderLineRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.SupplierOrderRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.SupplierProfileRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock private SupplierProfileRepository supplierRepository;
    @Mock private SupplierCatalogueItemRepository catalogueRepository;
    @Mock private SupplierOrderRepository orderRepository;
    @Mock private SupplierOrderLineRepository lineRepository;
    @InjectMocks private SupplierServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SUPPLIER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "buyer-1", "STAFF", "PROCUREMENT", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private SupplierProfileEntity supplier() {
        SupplierProfileEntity s = new SupplierProfileEntity();
        s.setSupplierId(SUPPLIER);
        s.setTenantId(TENANT);
        s.setName("Acme Pharma");
        s.setType(SupplierType.WHOLESALER);
        return s;
    }

    @Test
    @DisplayName("placeOrder computes line totals and order total")
    void placeOrder_computesTotals() {
        when(supplierRepository.findById(SUPPLIER)).thenReturn(Optional.of(supplier()));
        when(orderRepository.save(any(SupplierOrderEntity.class))).thenAnswer(inv -> {
            SupplierOrderEntity o = inv.getArgument(0);
            if (o.getSupplierOrderId() == null) o.setSupplierOrderId(UUID.randomUUID());
            return o;
        });
        when(lineRepository.save(any(SupplierOrderLineEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SupplierService.OrderLineInput> lines = List.of(
                new SupplierService.OrderLineInput("A", "Amoxicillin", 10, new BigDecimal("2.50")),
                new SupplierService.OrderLineInput("B", "Gloves", 4, new BigDecimal("1.25")));

        SupplierOrderEntity order = service.placeOrder(SUPPLIER, UUID.randomUUID(), "USD", lines, "routine");

        // 10*2.50 + 4*1.25 = 25.00 + 5.00 = 30.00
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(order.getStatus()).isEqualTo(SupplierOrderStatus.PLACED);
        verify(lineRepository, times(2)).save(any(SupplierOrderLineEntity.class));
    }

    @Test
    @DisplayName("placeOrder rejects an empty order")
    void placeOrder_empty() {
        when(supplierRepository.findById(SUPPLIER)).thenReturn(Optional.of(supplier()));
        assertThatThrownBy(() -> service.placeOrder(SUPPLIER, null, "USD", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    @DisplayName("updateOrderStatus to DELIVERED stamps dispatch and delivery times")
    void updateOrderStatus_delivered() {
        UUID orderId = UUID.randomUUID();
        SupplierOrderEntity order = new SupplierOrderEntity();
        order.setSupplierOrderId(orderId);
        order.setTenantId(TENANT);
        order.setStatus(SupplierOrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(SupplierOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierOrderEntity result = service.updateOrderStatus(orderId, SupplierOrderStatus.DELIVERED);

        assertThat(result.getStatus()).isEqualTo(SupplierOrderStatus.DELIVERED);
        assertThat(result.getDispatchedAt()).isNotNull();
        assertThat(result.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("updateOrderStatus rejects a terminal order")
    void updateOrderStatus_terminal() {
        UUID orderId = UUID.randomUUID();
        SupplierOrderEntity order = new SupplierOrderEntity();
        order.setSupplierOrderId(orderId);
        order.setTenantId(TENANT);
        order.setStatus(SupplierOrderStatus.DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderStatus(orderId, SupplierOrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }

    @Test
    @DisplayName("upsertCatalogueItem creates then updates by item name")
    void upsertCatalogueItem() {
        when(supplierRepository.findById(SUPPLIER)).thenReturn(Optional.of(supplier()));
        when(catalogueRepository.findByTenantIdAndSupplierIdAndItemName(TENANT, SUPPLIER, "Amoxicillin"))
                .thenReturn(Optional.empty());
        when(catalogueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var item = service.upsertCatalogueItem(SUPPLIER, "AMOX", "Amoxicillin", "100s",
                new BigDecimal("12.00"), "USD", 500);

        assertThat(item.getItemName()).isEqualTo("Amoxicillin");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("12.00");
        assertThat(item.getAvailableQty()).isEqualTo(500);
    }
}
