package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.sharedkernel.terminology.Coding;
import zw.gov.mohcc.impilo.sharedkernel.terminology.CodingSystem;
import zw.gov.mohcc.impilo.sharedkernel.terminology.CodingValidator;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages order lifecycle state transitions.
 *
 * <p>Enforces valid state transitions via a transition map and publishes
 * domain events to the outbox for each transition. Generates ULID-based
 * order identifiers on placement.</p>
 */
@Service
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(OrderStatus.DRAFT, Set.of(OrderStatus.PLACED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PLACED, Set.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.ACCEPTED, Set.of(OrderStatus.SCHEDULED, OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.SCHEDULED, Set.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.IN_PROGRESS, Set.of(OrderStatus.PARTIAL_RESULT, OrderStatus.RESULT_AVAILABLE, OrderStatus.FAILED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PARTIAL_RESULT, Set.of(OrderStatus.RESULT_AVAILABLE, OrderStatus.FAILED)),
            Map.entry(OrderStatus.RESULT_AVAILABLE, Set.of(OrderStatus.REVIEWED)),
            Map.entry(OrderStatus.REVIEWED, Set.of(OrderStatus.RELEASED)),
            Map.entry(OrderStatus.RELEASED, Set.of(OrderStatus.COMPLETED)),
            Map.entry(OrderStatus.COMPLETED, Set.of()),
            Map.entry(OrderStatus.CANCELLED, Set.of()),
            Map.entry(OrderStatus.REJECTED, Set.of()),
            Map.entry(OrderStatus.FAILED, Set.of(OrderStatus.IN_PROGRESS))
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderStateMachine(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Inner class for order item data transfer during order placement.
     */
    public record OrderItemData(
            String codingSystem,
            String code,
            String displayName,
            int quantity,
            String instructions,
            String specimenType,
            String bodySite
    ) {}

    /**
     * Place a new order. Generates a ULID, creates the order entity and items,
     * writes an outbox event, and returns the persisted order.
     */
    @Transactional
    public OrderEntity placeOrder(UUID facilityId, String patientCpid, OrderType type,
                                  OrderPriority priority, String ziboCode,
                                  String encounterRef, String clinicalNotes,
                                  List<OrderItemData> items) {
        TrustContext ctx = TrustContextHolder.require();

        String orderId = generateUlid();

        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setTenantId(ctx.tenantId());
        order.setFacilityId(facilityId);
        order.setPatientCpid(patientCpid);
        order.setOrderType(type);
        order.setPriority(priority != null ? priority : OrderPriority.ROUTINE);
        order.setStatus(OrderStatus.PLACED);
        order.setPlacedBy(ctx.actorId());
        order.setPlacedAt(OffsetDateTime.now());
        order.setWorkspaceId(ctx.workspaceId());
        order.setEncounterRef(encounterRef);
        order.setZiboOrderCode(ziboCode);
        order.setClinicalNotes(clinicalNotes);

        order = orderRepository.save(order);

        if (items != null) {
            for (OrderItemData itemData : items) {
                // Validate coding system if provided
                String systemUri = itemData.codingSystem() != null
                        ? itemData.codingSystem() : CodingSystem.IMPILO_LOCAL.getUri();
                Coding coding = Coding.of(systemUri, itemData.code(), itemData.displayName());
                List<String> codingIssues = CodingValidator.validate(coding);
                if (!codingIssues.isEmpty()) {
                    log.warn("Order item coding validation warnings for code '{}': {}",
                            itemData.code(), String.join("; ", codingIssues));
                }

                OrderItemEntity item = new OrderItemEntity();
                item.setOrderId(orderId);
                item.setCodingSystem(systemUri);
                item.setCode(itemData.code());
                item.setDisplayName(itemData.displayName());
                item.setQuantity(itemData.quantity());
                item.setInstructions(itemData.instructions());
                item.setSpecimenType(itemData.specimenType());
                item.setBodySite(itemData.bodySite());
                orderItemRepository.save(item);
            }
        }

        publishEvent("ORDER", orderId, "ORDER_PLACED", order, ctx.tenantId());

        log.info("Order placed: orderId={}, type={}, facility={}", orderId, type, facilityId);
        return order;
    }

    /**
     * Transition an order to a new status. Validates the transition
     * against the state machine and publishes an outbox event.
     */
    @Transactional
    public OrderEntity transition(OrderEntity order, OrderStatus newStatus) {
        OrderStatus current = order.getStatus();
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(current, Set.of());

        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid order transition from %s to %s for order %s",
                            current, newStatus, order.getOrderId()));
        }

        TrustContext ctx = TrustContextHolder.require();
        order.setStatus(newStatus);
        order = orderRepository.save(order);

        String eventType = "ORDER_" + newStatus.name();
        publishEvent("ORDER", order.getOrderId(), eventType, order, ctx.tenantId());

        log.info("Order transitioned: orderId={}, {} -> {}", order.getOrderId(), current, newStatus);
        return order;
    }

    /**
     * Cancel an order with a reason. Only valid from non-terminal states.
     */
    @Transactional
    public OrderEntity cancelOrder(String orderId, String reason) {
        OrderEntity order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setClinicalNotes(
                (order.getClinicalNotes() != null ? order.getClinicalNotes() + "\n" : "")
                        + "CANCELLATION: " + reason);

        return transition(order, OrderStatus.CANCELLED);
    }

    /**
     * Get an order by its ID.
     */
    public OrderEntity getOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    /**
     * Get all orders for a patient by CPID within the current tenant.
     */
    public List<OrderEntity> getPatientOrders(String patientCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return orderRepository.findByTenantIdAndPatientCpid(ctx.tenantId(), patientCpid);
    }

    /**
     * Check whether a transition from the current status to the target status is allowed.
     *
     * @param current the current order status
     * @param target  the desired target status
     * @return true if the transition is permitted
     */
    public boolean isTransitionAllowed(OrderStatus current, OrderStatus target) {
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    /**
     * Returns the set of valid target statuses for the given current status.
     *
     * @param current the current order status
     * @return an unmodifiable set of valid target statuses
     */
    public Set<OrderStatus> getAllowedTransitions(OrderStatus current) {
        return TRANSITIONS.getOrDefault(current, Set.of());
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

    /**
     * Generate a ULID-like identifier (26 characters, time-sortable).
     * Uses a simplified approach: base32-encoded timestamp + random component.
     */
    static String generateUlid() {
        long timestamp = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(26);
        String base32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        Random random = new Random();

        // Encode 48-bit timestamp in 10 characters
        for (int i = 9; i >= 0; i--) {
            sb.append(base32.charAt((int) (timestamp & 0x1F)));
            timestamp >>>= 5;
        }
        sb.reverse();

        // 16 random characters
        for (int i = 0; i < 16; i++) {
            sb.append(base32.charAt(random.nextInt(32)));
        }

        return sb.toString();
    }
}
