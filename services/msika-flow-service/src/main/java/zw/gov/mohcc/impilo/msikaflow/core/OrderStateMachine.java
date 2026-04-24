package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED, Set.of(OrderStatus.VALIDATED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.VALIDATED, Set.of(OrderStatus.PRICED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PRICED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID, Set.of(OrderStatus.ROUTED, OrderStatus.CANCELLED, OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.ROUTED, Set.of(OrderStatus.ACCEPTED, OrderStatus.FAILED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.ACCEPTED, Set.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.IN_PROGRESS, Set.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.BOOKED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.READY_FOR_PICKUP, Set.of(OrderStatus.COLLECTED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.BOOKED, Set.of(OrderStatus.ATTENDED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.COLLECTED, Set.of(OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, Set.of(OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(OrderStatus.ATTENDED, Set.of(OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED, Set.of(OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, Set.of(OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUND_PENDING, Set.of(OrderStatus.REFUNDED, OrderStatus.FAILED));
        // Terminal states
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(OrderStatus.FAILED, Set.of(OrderStatus.ROUTED, OrderStatus.CANCELLED)); // retry from failed
    }

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderEventRepository orderEventRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderStateMachine(OrderRepository orderRepository,
                            OrderLineRepository orderLineRepository,
                            OrderEventRepository orderEventRepository,
                            EventOutboxRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.orderEventRepository = orderEventRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderEntity createOrder(UUID tenantId, String actorId, ActorType actorType,
                                   String patientCpid, OrderType orderType,
                                   UUID facilityId, String facilityRef,
                                   UUID vendorId, String vendorRef,
                                   String providerRef,
                                   String idempotencyKey) {
        // Idempotency check
        if (idempotencyKey != null) {
            Optional<OrderEntity> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent order return: key={}", idempotencyKey);
                return existing.get();
            }
        }

        OrderEntity order = new OrderEntity();
        order.setOrderId(UlidGenerator.generate());
        order.setTenantId(tenantId);
        order.setActorId(actorId);
        order.setActorType(actorType);
        order.setPatientCpid(patientCpid);
        order.setOrderType(orderType);
        order.setStatus(OrderStatus.CREATED);
        order.setFacilityId(facilityId);
        order.setFacilityRef(facilityRef);
        order.setVendorId(vendorId);
        order.setVendorRef(vendorRef);
        order.setProviderRef(providerRef);
        order.setAmountTotal(BigDecimal.ZERO);
        order.setCurrency("ZWG");
        order.setIdempotencyKey(idempotencyKey);

        orderRepository.save(order);
        recordEvent(order, null, OrderStatus.CREATED, actorId, actorType.name(), "ORDER_CREATED", null);
        publishOutbox(order, "ORDER_CREATED");

        log.info("Order created: id={} type={} tenant={}", order.getOrderId(), orderType, tenantId);
        return order;
    }

    @Transactional
    public OrderEntity transition(String orderId, OrderStatus targetStatus,
                                  String actorId, String actorType,
                                  String reasonCode, String note) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        OrderStatus current = order.getStatus();
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Collections.emptySet());

        if (!allowed.contains(targetStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid transition: %s -> %s for order %s", current, targetStatus, orderId));
        }

        order.setStatus(targetStatus);
        orderRepository.save(order);

        recordEvent(order, current, targetStatus, actorId, actorType, reasonCode, note);

        String eventType = "ORDER_" + targetStatus.name();
        publishOutbox(order, eventType);

        log.info("Order transition: id={} {} -> {}", orderId, current, targetStatus);
        return order;
    }

    public OrderEntity getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    public List<OrderLineEntity> getOrderLines(String orderId) {
        return orderLineRepository.findByOrderId(orderId);
    }

    public List<OrderEventEntity> getOrderEvents(String orderId) {
        return orderEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional
    public OrderLineEntity addLine(String orderId, String msikaCoreCode, LineItemKind kind,
                                   int qty, BigDecimal unitPrice, FulfillmentMode fulfillmentMode,
                                   String restrictions, String substitutionPolicy, String metadata) {
        OrderEntity order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.VALIDATED) {
            throw new IllegalStateException("Cannot add lines to order in status: " + order.getStatus());
        }

        OrderLineEntity line = new OrderLineEntity();
        line.setLineId(UlidGenerator.generate());
        line.setOrderId(orderId);
        line.setMsikaCoreCode(msikaCoreCode);
        line.setKind(kind);
        line.setQty(qty);
        line.setUnitPrice(unitPrice);
        line.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));
        line.setFulfillmentMode(fulfillmentMode);
        line.setRestrictions(restrictions);
        line.setSubstitutionPolicy(substitutionPolicy);
        line.setState(LineItemState.REQUESTED);
        line.setMetadata(metadata);

        orderLineRepository.save(line);
        recalculateTotal(order);
        return line;
    }

    @Transactional
    public void updatePriceSnapshot(String orderId, String priceSnapshot) {
        OrderEntity order = getOrder(orderId);
        order.setPriceSnapshot(priceSnapshot);
        recalculateTotal(order);
        orderRepository.save(order);
    }

    private void recalculateTotal(OrderEntity order) {
        List<OrderLineEntity> lines = orderLineRepository.findByOrderId(order.getOrderId());
        BigDecimal total = lines.stream()
                .map(OrderLineEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setAmountTotal(total);
        orderRepository.save(order);
    }

    private void recordEvent(OrderEntity order, OrderStatus fromState, OrderStatus toState,
                             String actorId, String actorType, String reasonCode, String note) {
        OrderEventEntity event = new OrderEventEntity();
        event.setId(UlidGenerator.generate());
        event.setOrderId(order.getOrderId());
        event.setFromState(fromState != null ? fromState.name() : null);
        event.setToState(toState.name());
        event.setActorId(actorId);
        event.setActorType(actorType);
        event.setReasonCode(reasonCode);
        event.setNote(note);
        orderEventRepository.save(event);
    }

    private void publishOutbox(OrderEntity order, String eventType) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("Order");
            outbox.setAggregateId(order.getOrderId());
            outbox.setEventType(eventType);
            java.util.Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", order.getOrderId());
            payload.put("tenantId", order.getTenantId().toString());
            payload.put("status", order.getStatus().name());
            payload.put("orderType", order.getOrderType().name());
            payload.put("actorId", order.getActorId());
            payload.put("amountTotal", order.getAmountTotal().toPlainString());
            payload.put("currency", order.getCurrency());
            if (order.getPatientCpid() != null && !order.getPatientCpid().isBlank()) {
                payload.put("patientCpid", order.getPatientCpid());
            }
            if (order.getFacilityId() != null) {
                payload.put("facilityId", order.getFacilityId().toString());
            }
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(order.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}
