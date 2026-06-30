package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.SupplierOrderStatus;
import zw.gov.mohcc.impilo.inventory.domain.SupplierType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierCatalogueItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierProfileEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Dura supplier/seller mode service: supplier profiles, published catalogue with
 * pricing/availability, and customer orders with a fulfilment lifecycle.
 *
 * <p>Costing/payment (Costa/MusheX) and dispatch (Nhume) integrate later; order
 * status carries PACKED/DISPATCHED/DELIVERED in the interim.</p>
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface SupplierService {

    SupplierProfileEntity registerSupplier(String name, SupplierType type, String licenceNumber, String contact);

    List<SupplierProfileEntity> listSuppliers();

    SupplierProfileEntity getSupplier(UUID supplierId);

    /**
     * Create or update a supplier catalogue item (keyed by supplier + item name).
     */
    SupplierCatalogueItemEntity upsertCatalogueItem(UUID supplierId, String itemCode, String itemName,
                                                    String packSize, BigDecimal unitPrice, String currency,
                                                    int availableQty);

    List<SupplierCatalogueItemEntity> listCatalogue(UUID supplierId);

    /**
     * Place a customer order with a supplier; line totals and order total are computed.
     */
    SupplierOrderEntity placeOrder(UUID supplierId, UUID buyerFacilityId, String currency,
                                   List<OrderLineInput> lines, String notes);

    SupplierOrderEntity getOrder(UUID supplierOrderId);

    List<SupplierOrderLineEntity> orderLines(UUID supplierOrderId);

    List<SupplierOrderEntity> listOrders(UUID supplierId);

    /**
     * Advance a supplier order's status; DISPATCHED/DELIVERED stamp their times.
     */
    SupplierOrderEntity updateOrderStatus(UUID supplierOrderId, SupplierOrderStatus status);

    /** Input for an order line. */
    record OrderLineInput(String itemCode, String itemName, int qty, BigDecimal unitPrice) {}
}
