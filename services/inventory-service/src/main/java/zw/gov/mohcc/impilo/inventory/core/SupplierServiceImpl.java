package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.SupplierOrderStatus;
import zw.gov.mohcc.impilo.inventory.domain.SupplierType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierCatalogueItemEntity;
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
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the Dura supplier/seller mode service.
 */
@Service
public class SupplierServiceImpl implements SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierServiceImpl.class);

    private static final Set<SupplierOrderStatus> TERMINAL =
            EnumSet.of(SupplierOrderStatus.DELIVERED, SupplierOrderStatus.CANCELLED, SupplierOrderStatus.REJECTED);

    private final SupplierProfileRepository supplierRepository;
    private final SupplierCatalogueItemRepository catalogueRepository;
    private final SupplierOrderRepository orderRepository;
    private final SupplierOrderLineRepository lineRepository;

    public SupplierServiceImpl(SupplierProfileRepository supplierRepository,
                               SupplierCatalogueItemRepository catalogueRepository,
                               SupplierOrderRepository orderRepository,
                               SupplierOrderLineRepository lineRepository) {
        this.supplierRepository = supplierRepository;
        this.catalogueRepository = catalogueRepository;
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
    }

    @Override
    @Transactional
    public SupplierProfileEntity registerSupplier(String name, SupplierType type, String licenceNumber, String contact) {
        TrustContext ctx = TrustContextHolder.require();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        SupplierProfileEntity supplier = new SupplierProfileEntity();
        supplier.setTenantId(ctx.tenantId());
        supplier.setName(name);
        supplier.setType(type != null ? type : SupplierType.DISTRIBUTOR);
        supplier.setLicenceNumber(licenceNumber);
        supplier.setContact(contact);
        supplier = supplierRepository.save(supplier);
        log.info("Supplier registered: name={}, type={}", name, supplier.getType());
        return supplier;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierProfileEntity> listSuppliers() {
        TrustContext ctx = TrustContextHolder.require();
        return supplierRepository.findByTenantIdOrderByName(ctx.tenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierProfileEntity getSupplier(UUID supplierId) {
        return loadOwnedSupplier(supplierId);
    }

    @Override
    @Transactional
    public SupplierCatalogueItemEntity upsertCatalogueItem(UUID supplierId, String itemCode, String itemName,
                                                           String packSize, BigDecimal unitPrice, String currency,
                                                           int availableQty) {
        TrustContext ctx = TrustContextHolder.require();
        loadOwnedSupplier(supplierId);
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (availableQty < 0) {
            throw new IllegalArgumentException("availableQty must not be negative");
        }

        SupplierCatalogueItemEntity item = catalogueRepository
                .findByTenantIdAndSupplierIdAndItemName(ctx.tenantId(), supplierId, itemName)
                .orElseGet(() -> {
                    SupplierCatalogueItemEntity c = new SupplierCatalogueItemEntity();
                    c.setTenantId(ctx.tenantId());
                    c.setSupplierId(supplierId);
                    c.setItemName(itemName);
                    return c;
                });
        item.setItemCode(itemCode);
        item.setPackSize(packSize);
        item.setUnitPrice(unitPrice != null ? unitPrice : BigDecimal.ZERO);
        if (currency != null && !currency.isBlank()) {
            item.setCurrency(currency);
        }
        item.setAvailableQty(availableQty);
        item = catalogueRepository.save(item);
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierCatalogueItemEntity> listCatalogue(UUID supplierId) {
        TrustContext ctx = TrustContextHolder.require();
        loadOwnedSupplier(supplierId);
        return catalogueRepository.findByTenantIdAndSupplierIdOrderByItemName(ctx.tenantId(), supplierId);
    }

    @Override
    @Transactional
    public SupplierOrderEntity placeOrder(UUID supplierId, UUID buyerFacilityId, String currency,
                                          List<OrderLineInput> lines, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        loadOwnedSupplier(supplierId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("order must have at least one line");
        }

        SupplierOrderEntity order = new SupplierOrderEntity();
        order.setTenantId(ctx.tenantId());
        order.setSupplierId(supplierId);
        order.setBuyerFacilityId(buyerFacilityId);
        order.setStatus(SupplierOrderStatus.PLACED);
        order.setCurrency(currency != null && !currency.isBlank() ? currency : "USD");
        order.setPlacedBy(ctx.actorId());
        order.setNotes(notes);
        order.setTotalAmount(BigDecimal.ZERO);
        order = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderLineInput in : lines) {
            if (in.qty() <= 0) {
                throw new IllegalArgumentException("line qty must be positive");
            }
            BigDecimal unit = in.unitPrice() != null ? in.unitPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(in.qty()));
            SupplierOrderLineEntity line = new SupplierOrderLineEntity();
            line.setSupplierOrderId(order.getSupplierOrderId());
            line.setItemCode(in.itemCode());
            line.setItemName(in.itemName());
            line.setQty(in.qty());
            line.setUnitPrice(unit);
            line.setLineTotal(lineTotal);
            lineRepository.save(line);
            total = total.add(lineTotal);
        }
        order.setTotalAmount(total);
        order = orderRepository.save(order);
        log.info("Supplier order placed: supplier={}, lines={}, total={}", supplierId, lines.size(), total);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierOrderEntity getOrder(UUID supplierOrderId) {
        return loadOwnedOrder(supplierOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierOrderLineEntity> orderLines(UUID supplierOrderId) {
        loadOwnedOrder(supplierOrderId);
        return lineRepository.findBySupplierOrderId(supplierOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierOrderEntity> listOrders(UUID supplierId) {
        TrustContext ctx = TrustContextHolder.require();
        if (supplierId != null) {
            return orderRepository.findByTenantIdAndSupplierIdOrderByCreatedAtDesc(ctx.tenantId(), supplierId);
        }
        return orderRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
    }

    @Override
    @Transactional
    public SupplierOrderEntity updateOrderStatus(UUID supplierOrderId, SupplierOrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        SupplierOrderEntity order = loadOwnedOrder(supplierOrderId);
        if (TERMINAL.contains(order.getStatus())) {
            throw new IllegalStateException("Order is already " + order.getStatus());
        }
        order.setStatus(status);
        if (status == SupplierOrderStatus.DISPATCHED && order.getDispatchedAt() == null) {
            order.setDispatchedAt(OffsetDateTime.now());
        }
        if (status == SupplierOrderStatus.DELIVERED) {
            if (order.getDispatchedAt() == null) {
                order.setDispatchedAt(OffsetDateTime.now());
            }
            order.setDeliveredAt(OffsetDateTime.now());
        }
        order = orderRepository.save(order);
        log.info("Supplier order {} -> {}", supplierOrderId, status);
        return order;
    }

    private SupplierProfileEntity loadOwnedSupplier(UUID supplierId) {
        TrustContext ctx = TrustContextHolder.require();
        SupplierProfileEntity supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));
        if (supplier.getTenantId() == null || !supplier.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Supplier does not belong to the current tenant");
        }
        return supplier;
    }

    private SupplierOrderEntity loadOwnedOrder(UUID supplierOrderId) {
        TrustContext ctx = TrustContextHolder.require();
        SupplierOrderEntity order = orderRepository.findById(supplierOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier order not found: " + supplierOrderId));
        if (order.getTenantId() == null || !order.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Supplier order does not belong to the current tenant");
        }
        return order;
    }
}
