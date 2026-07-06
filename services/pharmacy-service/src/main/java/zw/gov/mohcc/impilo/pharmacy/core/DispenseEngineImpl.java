package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickItemData;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Core orchestration service for the pharmacy dispense workflow.
 *
 * <p>Manages the full lifecycle of a dispense order from receipt through
 * acceptance, picking, dispensing, and completion. Enforces valid state
 * transitions via an {@link EnumMap} transition table and publishes domain
 * events to the outbox for each state change.</p>
 *
 * <h3>State Transitions</h3>
 * <pre>
 *   PENDING -> ACCEPTED -> PICKING -> DISPENSED / PARTIALLY_DISPENSED / READY
 *   PENDING / ACCEPTED -> CANCELLED / REJECTED
 *   PICKING -> DISPENSED / PARTIALLY_DISPENSED
 *   READY -> DISPENSED / PARTIALLY_DISPENSED / CANCELLED
 *   PARTIALLY_DISPENSED -> DISPENSED / CANCELLED
 *   DISPENSED -> CANCELLED (via reversal only)
 * </pre>
 */
@Service
public class DispenseEngineImpl implements DispenseEngine {

    private static final Logger log = LoggerFactory.getLogger(DispenseEngineImpl.class);

    /** Valid state transitions for dispense orders. */
    private static final EnumMap<DispenseStatus, Set<DispenseStatus>> TRANSITIONS;

    static {
        TRANSITIONS = new EnumMap<>(DispenseStatus.class);
        TRANSITIONS.put(DispenseStatus.PENDING,
                EnumSet.of(DispenseStatus.ACCEPTED, DispenseStatus.REJECTED, DispenseStatus.CANCELLED));
        TRANSITIONS.put(DispenseStatus.ACCEPTED,
                EnumSet.of(DispenseStatus.PICKING, DispenseStatus.REJECTED, DispenseStatus.CANCELLED));
        TRANSITIONS.put(DispenseStatus.PICKING,
                EnumSet.of(DispenseStatus.READY, DispenseStatus.DISPENSED, DispenseStatus.PARTIALLY_DISPENSED));
        TRANSITIONS.put(DispenseStatus.READY,
                EnumSet.of(DispenseStatus.DISPENSED, DispenseStatus.PARTIALLY_DISPENSED, DispenseStatus.CANCELLED));
        TRANSITIONS.put(DispenseStatus.PARTIALLY_DISPENSED,
                EnumSet.of(DispenseStatus.DISPENSED, DispenseStatus.CANCELLED));
        TRANSITIONS.put(DispenseStatus.DISPENSED,
                EnumSet.of(DispenseStatus.CANCELLED));
        TRANSITIONS.put(DispenseStatus.CANCELLED, EnumSet.noneOf(DispenseStatus.class));
        TRANSITIONS.put(DispenseStatus.REJECTED, EnumSet.noneOf(DispenseStatus.class));
    }

    private final DispenseOrderRepository orderRepository;
    private final DispenseItemRepository itemRepository;
    private final StockLedgerService stockLedgerService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DispenseEngineImpl(DispenseOrderRepository orderRepository,
                              DispenseItemRepository itemRepository,
                              StockLedgerService stockLedgerService,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.stockLedgerService = stockLedgerService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public DispenseOrderEntity createFromOrosOrder(String orosOrderId, String patientCpid,
                                                    UUID facilityId, String prescriberNotes,
                                                    List<OrderItemData> items) {
        TrustContext ctx = TrustContextHolder.require();

        DispenseOrderEntity order = new DispenseOrderEntity();
        order.setTenantId(ctx.tenantId());
        order.setFacilityId(facilityId);
        order.setWorkspaceId(ctx.workspaceId());
        order.setPatientCpid(patientCpid);
        order.setOrosOrderId(orosOrderId);
        order.setStatus(DispenseStatus.PENDING);
        order.setPriority(OrderPriority.ROUTINE);
        order.setPrescriberNotes(prescriberNotes);

        order = orderRepository.save(order);

        if (items != null) {
            for (OrderItemData itemData : items) {
                DispenseItemEntity item = new DispenseItemEntity();
                item.setDispenseOrderId(order.getOrderId());
                item.setDrugCode(itemData.drugCode());
                item.setDrugDisplay(itemData.drugDisplay());
                item.setQtyRequested(itemData.qtyRequested());
                item.setQtyRemaining(itemData.qtyRequested());
                item.setStatus(DispenseItemStatus.PENDING);
                item.setInstructions(itemData.instructions());
                itemRepository.save(item);
            }
        }

        publishEvent("DISPENSE_ORDER", order.getOrderId().toString(),
                "DISPENSE_ORDER_RECEIVED", order, ctx.tenantId());

        log.info("Dispense order created: orderId={}, orosOrderId={}, facility={}",
                order.getOrderId(), orosOrderId, facilityId);
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity acceptOrder(UUID orderId) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        transition(order, DispenseStatus.ACCEPTED);

        order.setAcceptedBy(ctx.actorId());
        order.setAcceptedAt(OffsetDateTime.now());
        order = orderRepository.save(order);

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "DISPENSE_ACCEPTED", order, ctx.tenantId());

        log.info("Dispense order accepted: orderId={}, actor={}", orderId, ctx.actorId());
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity pickItems(UUID orderId, List<PickItemData> picks) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        // Transition to PICKING if this is the first pick
        if (order.getStatus() == DispenseStatus.ACCEPTED) {
            transition(order, DispenseStatus.PICKING);
            order = orderRepository.save(order);
        }

        for (PickItemData pick : picks) {
            DispenseItemEntity item = itemRepository.findById(pick.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + pick.itemId()));

            if (!item.getDispenseOrderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "Item " + pick.itemId() + " does not belong to order " + orderId);
            }

            // Validate batch not expired
            if (pick.expiryDate() != null && pick.expiryDate().isBefore(LocalDate.now())) {
                throw new IllegalStateException(
                        "Batch " + pick.batchNumber() + " has expired on " + pick.expiryDate());
            }

            item.setBatchNumber(pick.batchNumber());
            item.setExpiryDate(pick.expiryDate());
            item.setBarcode(pick.barcode());
            item.setQtyDispensed(pick.qtyPicked());
            item.setQtyRemaining(item.getQtyRequested() - pick.qtyPicked());
            item.setStatus(DispenseItemStatus.PICKED);
            item.setPickedBy(ctx.actorId());
            item.setPickedAt(OffsetDateTime.now());
            itemRepository.save(item);
        }

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "DISPENSE_PICKING", order, ctx.tenantId());

        log.info("Items picked for order: orderId={}, pickCount={}", orderId, picks.size());
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity partialDispense(UUID orderId) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        transition(order, DispenseStatus.PARTIALLY_DISPENSED);

        List<DispenseItemEntity> items =
                itemRepository.findByDispenseOrderIdOrderByCreatedAtAsc(orderId);

        for (DispenseItemEntity item : items) {
            if (item.getQtyDispensed() > 0 && item.getStatus() == DispenseItemStatus.PICKED) {
                item.setStatus(DispenseItemStatus.DISPENSED);
                item.setDispensedBy(ctx.actorId());
                item.setDispensedAt(OffsetDateTime.now());
                itemRepository.save(item);

                // Record stock movement (negative delta = stock going out)
                stockLedgerService.recordMovement(
                        order.getFacilityId(),
                        order.getStoreId() != null ? order.getStoreId() : "DEFAULT",
                        item.getDrugCode(),
                        item.getBatchNumber(),
                        item.getExpiryDate(),
                        -item.getQtyDispensed(),
                        MovementType.DISPENSE,
                        "PATIENT_DISPENSE",
                        "DISPENSE_ORDER",
                        orderId.toString());
                publishStockMovementRequested(order, item, ctx.tenantId(), ctx.actorId());
            }
        }

        order = orderRepository.save(order);

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "DISPENSE_PARTIAL", order, ctx.tenantId());

        log.info("Partial dispense completed: orderId={}", orderId);
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity completeDispense(UUID orderId) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        transition(order, DispenseStatus.DISPENSED);

        List<DispenseItemEntity> items =
                itemRepository.findByDispenseOrderIdOrderByCreatedAtAsc(orderId);

        for (DispenseItemEntity item : items) {
            if (item.getStatus() == DispenseItemStatus.PICKED) {
                item.setStatus(DispenseItemStatus.DISPENSED);
                item.setDispensedBy(ctx.actorId());
                item.setDispensedAt(OffsetDateTime.now());
                itemRepository.save(item);

                // Record stock movement
                stockLedgerService.recordMovement(
                        order.getFacilityId(),
                        order.getStoreId() != null ? order.getStoreId() : "DEFAULT",
                        item.getDrugCode(),
                        item.getBatchNumber(),
                        item.getExpiryDate(),
                        -item.getQtyDispensed(),
                        MovementType.DISPENSE,
                        "PATIENT_DISPENSE",
                        "DISPENSE_ORDER",
                        orderId.toString());
                publishStockMovementRequested(order, item, ctx.tenantId(), ctx.actorId());
            }
        }

        order.setCompletedBy(ctx.actorId());
        order.setCompletedAt(OffsetDateTime.now());
        order = orderRepository.save(order);

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "DISPENSE_COMPLETED", order, ctx.tenantId());

        log.info("Dispense completed: orderId={}, completedBy={}", orderId, ctx.actorId());
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity backorder(UUID orderId, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        // Backorder maps to REJECTED (stock unavailability)
        transition(order, DispenseStatus.REJECTED);

        order.setClinicalNotes(
                (order.getClinicalNotes() != null ? order.getClinicalNotes() + "\n" : "")
                        + "BACKORDERED: " + notes);

        // Mark all remaining items as OUT_OF_STOCK
        List<DispenseItemEntity> items =
                itemRepository.findByDispenseOrderIdAndStatus(orderId, DispenseItemStatus.PENDING);
        for (DispenseItemEntity item : items) {
            item.setStatus(DispenseItemStatus.OUT_OF_STOCK);
            itemRepository.save(item);
        }

        order = orderRepository.save(order);

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "DISPENSE_BACKORDERED", order, ctx.tenantId());

        log.info("Dispense backordered: orderId={}, notes={}", orderId, notes);
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity cancelOrder(UUID orderId) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        transition(order, DispenseStatus.CANCELLED);

        // Cancel all pending items
        List<DispenseItemEntity> pendingItems =
                itemRepository.findByDispenseOrderIdAndStatus(orderId, DispenseItemStatus.PENDING);
        for (DispenseItemEntity item : pendingItems) {
            item.setStatus(DispenseItemStatus.CANCELLED);
            itemRepository.save(item);
        }

        order = orderRepository.save(order);

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "DISPENSE_CANCELLED", order, ctx.tenantId());

        log.info("Dispense cancelled: orderId={}, actor={}", orderId, ctx.actorId());
        return order;
    }

    @Override
    public DispenseOrderEntity getOrder(UUID orderId) {
        return requireOrder(orderId);
    }

    @Override
    public List<DispenseOrderEntity> getPatientOrders(String cpid) {
        return orderRepository.findByPatientCpidOrderByCreatedAtDesc(cpid);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Look up an order by ID or throw.
     */
    private DispenseOrderEntity requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Dispense order not found: " + orderId));
    }

    /**
     * Validate and apply a state transition.
     */
    private void transition(DispenseOrderEntity order, DispenseStatus target) {
        DispenseStatus current = order.getStatus();
        Set<DispenseStatus> allowed = TRANSITIONS.getOrDefault(current, EnumSet.noneOf(DispenseStatus.class));

        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    String.format("Invalid dispense transition from %s to %s for order %s",
                            current, target, order.getOrderId()));
        }

        order.setStatus(target);
        log.debug("Dispense order {} transitioned: {} -> {}", order.getOrderId(), current, target);
    }

    /**
     * Write a domain event to the transactional outbox.
     */
    /**
     * Emits the Dura (inventory-service) consumption contract — the consumer for
     * pharmacy.stock.movement.requested existed but nothing ever published it,
     * so facility dispensing never decremented sovereign stock (backlog ③).
     */
    private void publishStockMovementRequested(DispenseOrderEntity order, DispenseItemEntity item, UUID tenantId, String actorId) {
        Map<String, Object> movement = new LinkedHashMap<>();
        movement.put("tenantId", tenantId != null ? tenantId.toString() : null);
        movement.put("facilityId", order.getFacilityId() != null ? order.getFacilityId().toString() : null);
        movement.put("storeId", order.getStoreId() != null ? order.getStoreId() : "DEFAULT");
        movement.put("itemCode", item.getDrugCode());
        movement.put("batch", item.getBatchNumber());
        movement.put("expiry", item.getExpiryDate() != null ? item.getExpiryDate().toString() : null);
        movement.put("qty", item.getQtyDispensed());
        movement.put("orderId", order.getOrderId() != null ? order.getOrderId().toString() : null);
        movement.put("actorId", actorId);
        publishEvent("DISPENSE_ORDER", order.getOrderId().toString(), "STOCK_MOVEMENT_REQUESTED", movement, tenantId);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
