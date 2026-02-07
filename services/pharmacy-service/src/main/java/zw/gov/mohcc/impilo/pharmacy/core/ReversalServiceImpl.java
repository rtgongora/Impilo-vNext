package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.api.dto.ReturnItemData;
import zw.gov.mohcc.impilo.pharmacy.api.dto.WastageItemData;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles returns, wastage, and full reversals of dispense orders.
 *
 * <p>Processes the reverse logistics of the pharmacy workflow:</p>
 * <ul>
 *   <li><b>Returns</b>: patient returns medications, positive stock adjustment recorded</li>
 *   <li><b>Wastage</b>: items wasted (breakage, contamination), negative adjustment recorded</li>
 *   <li><b>Full reversal</b>: reverses ALL dispense movements for an order, publishes
 *       MUSHEX charge reversal event, and notifies OROS/PCT of corrected outcome</li>
 * </ul>
 */
@Service
public class ReversalServiceImpl implements ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalServiceImpl.class);

    private final DispenseOrderRepository orderRepository;
    private final DispenseItemRepository itemRepository;
    private final StockLedgerService stockLedgerService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReversalServiceImpl(DispenseOrderRepository orderRepository,
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
    public DispenseOrderEntity processReturn(UUID orderId, List<ReturnItemData> returns) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        for (ReturnItemData ret : returns) {
            DispenseItemEntity item = itemRepository.findById(ret.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + ret.itemId()));

            if (!item.getDispenseOrderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "Item " + ret.itemId() + " does not belong to order " + orderId);
            }

            // Record a RETURN stock movement (positive delta = stock coming back in)
            stockLedgerService.recordMovement(
                    order.getFacilityId(),
                    order.getStoreId() != null ? order.getStoreId() : "DEFAULT",
                    item.getDrugCode(),
                    item.getBatchNumber(),
                    item.getExpiryDate(),
                    ret.qtyReturned(),
                    MovementType.RETURN,
                    "PATIENT_RETURN",
                    "RETURN",
                    orderId.toString());

            // Update dispensed quantity
            item.setQtyDispensed(Math.max(0, item.getQtyDispensed() - ret.qtyReturned()));
            item.setQtyRemaining(item.getQtyRequested() - item.getQtyDispensed());
            item.setStatus(DispenseItemStatus.CANCELLED);
            itemRepository.save(item);
        }

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "RETURN_RECORDED", order, ctx.tenantId());

        log.info("Return processed: orderId={}, itemCount={}", orderId, returns.size());
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity processWastage(UUID orderId, List<WastageItemData> wastages) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        for (WastageItemData wst : wastages) {
            DispenseItemEntity item = itemRepository.findById(wst.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + wst.itemId()));

            if (!item.getDispenseOrderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "Item " + wst.itemId() + " does not belong to order " + orderId);
            }

            // Record a WRITE_OFF stock movement (negative delta = stock removed from inventory)
            stockLedgerService.recordMovement(
                    order.getFacilityId(),
                    order.getStoreId() != null ? order.getStoreId() : "DEFAULT",
                    item.getDrugCode(),
                    item.getBatchNumber(),
                    item.getExpiryDate(),
                    -wst.qtyWasted(),
                    MovementType.WASTAGE,
                    wst.reason() != null ? wst.reason() : "WASTAGE",
                    "WASTAGE",
                    orderId.toString());

            item.setStatus(DispenseItemStatus.CANCELLED);
            itemRepository.save(item);
        }

        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "WASTAGE_RECORDED", order, ctx.tenantId());

        log.info("Wastage processed: orderId={}, itemCount={}", orderId, wastages.size());
        return order;
    }

    @Override
    @Transactional
    public DispenseOrderEntity processReversal(UUID orderId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        DispenseOrderEntity order = requireOrder(orderId);

        DispenseStatus current = order.getStatus();
        if (current != DispenseStatus.DISPENSED && current != DispenseStatus.PARTIALLY_DISPENSED) {
            throw new IllegalStateException(
                    "Cannot reverse order in status " + current + " (must be DISPENSED or PARTIALLY_DISPENSED)");
        }

        // 1. Reverse ALL dispense stock movements for this order
        stockLedgerService.reverseMovements("DISPENSE_ORDER", orderId.toString(), reason);

        // 2. Reverse MUSHEX charges via outbox event
        Map<String, Object> mushexPayload = new LinkedHashMap<>();
        mushexPayload.put("orderId", orderId.toString());
        mushexPayload.put("orosOrderId", order.getOrosOrderId());
        mushexPayload.put("tenantId", order.getTenantId().toString());
        mushexPayload.put("facilityId", order.getFacilityId().toString());
        mushexPayload.put("patientCpid", order.getPatientCpid());
        mushexPayload.put("reason", reason);
        mushexPayload.put("action", "REVERSE");
        publishEvent("MUSHEX", orderId.toString(),
                "MUSHEX_CHARGE_REQUESTED", mushexPayload, ctx.tenantId());

        // 3. Update order status to CANCELLED (closest available terminal state for reversal)
        order.setStatus(DispenseStatus.CANCELLED);
        order.setClinicalNotes(
                (order.getClinicalNotes() != null ? order.getClinicalNotes() + "\n" : "")
                        + "REVERSED: " + reason);
        order.setCompletedBy(ctx.actorId());
        order = orderRepository.save(order);

        // 4. Cancel all items
        List<DispenseItemEntity> items =
                itemRepository.findByDispenseOrderIdOrderByCreatedAtAsc(orderId);
        for (DispenseItemEntity item : items) {
            item.setStatus(DispenseItemStatus.CANCELLED);
            itemRepository.save(item);
        }

        // 5. Publish reversal event (OROS and PCT will consume this)
        publishEvent("DISPENSE_ORDER", orderId.toString(),
                "REVERSAL_COMPLETED", order, ctx.tenantId());

        log.info("Full reversal completed: orderId={}, reason={}, actor={}",
                orderId, reason, ctx.actorId());
        return order;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private DispenseOrderEntity requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Dispense order not found: " + orderId));
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
