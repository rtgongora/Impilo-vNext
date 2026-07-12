package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.config.MsikaFlowProperties;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.NhumeClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final OrderStateMachine stateMachine;
    private final FulfillmentRouteRepository routeRepository;
    private final ReservationRepository reservationRepository;
    private final OrderLineRepository orderLineRepository;
    private final FulfillmentOrderRepository fulfillmentOrderRepository;
    private final DeliveryPlanRepository deliveryPlanRepository;
    private final DeliveryLegRepository deliveryLegRepository;
    private final CustodyRecordRepository custodyRecordRepository;
    private final HandoffEventRepository handoffEventRepository;
    private final SettlementRepository settlementRepository;
    private final NhumeClient nhumeClient;
    private final MsikaFlowProperties properties;
    private final ObjectMapper objectMapper;

    public FulfillmentService(OrderStateMachine stateMachine,
                              FulfillmentRouteRepository routeRepository,
                              ReservationRepository reservationRepository,
                              OrderLineRepository orderLineRepository,
                              FulfillmentOrderRepository fulfillmentOrderRepository,
                              DeliveryPlanRepository deliveryPlanRepository,
                              DeliveryLegRepository deliveryLegRepository,
                              CustodyRecordRepository custodyRecordRepository,
                              HandoffEventRepository handoffEventRepository,
                              SettlementRepository settlementRepository,
                              NhumeClient nhumeClient,
                              MsikaFlowProperties properties,
                              ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.routeRepository = routeRepository;
        this.reservationRepository = reservationRepository;
        this.orderLineRepository = orderLineRepository;
        this.fulfillmentOrderRepository = fulfillmentOrderRepository;
        this.deliveryPlanRepository = deliveryPlanRepository;
        this.deliveryLegRepository = deliveryLegRepository;
        this.custodyRecordRepository = custodyRecordRepository;
        this.handoffEventRepository = handoffEventRepository;
        this.settlementRepository = settlementRepository;
        this.nhumeClient = nhumeClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FulfillmentRouteEntity routeOrder(String orderId, String actorId, String actorType) {
        return routeOrder(orderId, actorId, actorType, null);
    }

    @Transactional
    public FulfillmentRouteEntity routeOrder(String orderId, String actorId, String actorType,
                                             HttpServletRequest inboundRequest) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Order must be PAID before routing. Current: " + order.getStatus());
        }

        List<OrderLineEntity> lines = orderLineRepository.findByOrderId(orderId);
        FulfillmentMode mode = determineRoute(order, lines);

        FulfillmentRouteEntity route = new FulfillmentRouteEntity();
        route.setId(UlidGenerator.generate());
        route.setOrderId(orderId);
        route.setRouteType(mode);
        route.setStatus(RouteStatus.ROUTED);

        try {
            Map<String, Object> targetRef = new LinkedHashMap<>();
            targetRef.put("facilityId", order.getFacilityId() != null ? order.getFacilityId().toString() : null);
            targetRef.put("vendorId", order.getVendorId() != null ? order.getVendorId().toString() : null);
            targetRef.put("mode", mode.name());
            route.setTargetRef(objectMapper.writeValueAsString(targetRef));
        } catch (Exception e) {
            log.warn("Failed to serialize target ref: {}", e.getMessage());
        }

        routeRepository.save(route);

        // Create fulfillment grouping and baseline delivery plan (multi-modal foundation)
        FulfillmentOrderEntity f = new FulfillmentOrderEntity();
        f.setFulfillmentId(UlidGenerator.generate());
        f.setOrderId(orderId);
        f.setFulfillmentType(order.getOrderType().name().contains("SERVICE") ? "SERVICE" : "PRODUCT");
        f.setStatus(FulfillmentOrderStatus.PENDING);
        if (order.getFacilityId() != null) f.setFacilityId(order.getFacilityId());
        if (order.getVendorId() != null) f.setVendorId(order.getVendorId());
        f = fulfillmentOrderRepository.save(f);

        // Map fulfillment route to delivery mode; supports human delivery + pickup + autonomy modes structurally.
        DeliveryMode deliveryMode = switch (mode) {
            case PHARMACY_PICKUP -> DeliveryMode.PICKUP;
            case HOME_DELIVERY -> DeliveryMode.HUMAN_DELIVERY;
            case OUTREACH_DELIVERY -> DeliveryMode.COMMUNITY_HANDOFF;
            case DIGITAL_FULFILLMENT -> DeliveryMode.HYBRID; // non-physical / hybrid flows
            case FACILITY_SERVICE -> DeliveryMode.FACILITY_HANDOFF;
        };

        // For now: generate a single plan; legs can be extended to multi-leg later.
        DeliveryPlanEntity plan = new DeliveryPlanEntity();
        plan.setPlanId(UlidGenerator.generate());
        plan.setFulfillmentId(f.getFulfillmentId());
        plan.setMode(deliveryMode);
        try {
            plan.setOriginJson(objectMapper.writeValueAsString(java.util.Map.of(
                    "facilityId", order.getFacilityId(),
                    "vendorId", order.getVendorId()
            )));
            plan.setDestinationJson(objectMapper.writeValueAsString(java.util.Map.of(
                    "patientCpid", order.getPatientCpid()
            )));
        } catch (Exception ignored) {}
        deliveryPlanRepository.save(plan);

        DeliveryLegEntity leg = new DeliveryLegEntity();
        leg.setLegId(UlidGenerator.generate());
        leg.setPlanId(plan.getPlanId());
        leg.setSequenceNumber(1);
        leg.setLegType("DIRECT");
        leg.setMode(deliveryMode == DeliveryMode.HYBRID ? DeliveryMode.HUMAN_DELIVERY : deliveryMode);
        leg.setOriginJson(plan.getOriginJson());
        leg.setDestinationJson(plan.getDestinationJson());
        deliveryLegRepository.save(leg);

        // Initial chain-of-custody: assign to origin (facility/vendor) as baseline.
        CustodyRecordEntity custody = new CustodyRecordEntity();
        custody.setCustodyId(UlidGenerator.generate());
        custody.setFulfillmentId(f.getFulfillmentId());
        custody.setCustodianType("ORIGIN");
        custody.setCustodianRef(order.getFacilityId() != null ? "facility:" + order.getFacilityId() : order.getVendorId() != null ? "vendor:" + order.getVendorId() : "unknown");
        custody.setConditionSummaryJson(JsonSupport.toJsonSafe(objectMapper, Map.of("note", "Initial custody at origin"), "{}"));
        custodyRecordRepository.save(custody);

        // Baseline handoff event for planning/audit (does not imply physical movement).
        HandoffEventEntity handoff = new HandoffEventEntity();
        handoff.setHandoffId(UlidGenerator.generate());
        handoff.setFulfillmentId(f.getFulfillmentId());
        handoff.setLegId(leg.getLegId());
        handoff.setHandoffType("PLAN_CREATED");
        handoff.setFromPartyJson(JsonSupport.toJsonSafe(objectMapper, Map.of("system", "msika-flow"), "{}"));
        handoff.setToPartyJson(JsonSupport.toJsonSafe(objectMapper, Map.of("state", "planned"), "{}"));
        handoff.setLocationJson(plan.getOriginJson());
        handoffEventRepository.save(handoff);

        // Create reservations for product lines
        for (OrderLineEntity line : lines) {
            if (line.getKind() == LineItemKind.PRODUCT) {
                createReservation(orderId, line);
            }
        }

        // Nhume is dispatch SoR for human/community delivery orders. Failure never
        // blocks routing — the manual out-for-delivery / mark-delivered endpoints are
        // the documented recovery path.
        if (properties.getFulfillment().isNhumeEnabled()
                && (deliveryMode == DeliveryMode.HUMAN_DELIVERY || deliveryMode == DeliveryMode.COMMUNITY_HANDOFF)) {
            dispatchToNhume(order, plan, inboundRequest);
        }

        stateMachine.transition(orderId, OrderStatus.ROUTED, actorId, actorType, "ORDER_ROUTED", "Route: " + mode);
        log.info("Order routed: id={} mode={}", orderId, mode);
        return route;
    }

    private void dispatchToNhume(OrderEntity order, DeliveryPlanEntity plan, HttpServletRequest inboundRequest) {
        String mushexIntentId = settlementRepository.findByOrderId(order.getOrderId())
                .map(SettlementEntity::getMushexPaymentIntentId)
                .orElse(null);
        nhumeClient.createMarketplaceDelivery(order, mushexIntentId, inboundRequest)
                .ifPresentOrElse(
                        created -> {
                            plan.setNhumeDeliveryId(created.deliveryId());
                            deliveryPlanRepository.save(plan);
                            log.info("Nhume delivery created: orderId={} nhumeDeliveryId={}", order.getOrderId(), created.deliveryId());
                        },
                        () -> log.warn("Nhume delivery dispatch failed for order {}; manual endpoints are the recovery path", order.getOrderId()));
    }

    private FulfillmentMode determineRoute(OrderEntity order, List<OrderLineEntity> lines) {
        // Check if any line has explicit fulfillment mode
        for (OrderLineEntity line : lines) {
            if (line.getFulfillmentMode() != null) {
                return line.getFulfillmentMode();
            }
        }

        // Default routing based on order type
        return switch (order.getOrderType()) {
            case OTC_PRODUCT_ORDER, RX_FULFILLMENT_ORDER -> FulfillmentMode.PHARMACY_PICKUP;
            case SERVICE_BOOKING_ORDER -> FulfillmentMode.FACILITY_SERVICE;
            case BUNDLE_ORDER -> FulfillmentMode.PHARMACY_PICKUP;
        };
    }

    private void createReservation(String orderId, OrderLineEntity line) {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(UlidGenerator.generate());
        reservation.setOrderId(orderId);
        reservation.setLineId(line.getLineId());
        reservation.setSystem("INVENTORY");
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setExpiresAt(OffsetDateTime.now().plusHours(24));
        reservationRepository.save(reservation);
    }

    @Transactional
    public void acceptOrder(String orderId, String actorId, String actorType) {
        stateMachine.transition(orderId, OrderStatus.ACCEPTED, actorId, actorType, "ORDER_ACCEPTED", null);
        Optional<FulfillmentRouteEntity> route = routeRepository.findByOrderId(orderId);
        route.ifPresent(r -> {
            r.setStatus(RouteStatus.ACCEPTED);
            routeRepository.save(r);
        });
    }

    @Transactional
    public void markReady(String orderId, String actorId, String actorType) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new IllegalStateException("Order must be ACCEPTED or IN_PROGRESS to mark ready");
        }
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            stateMachine.transition(orderId, OrderStatus.IN_PROGRESS, actorId, actorType, "IN_PROGRESS", null);
        }
        stateMachine.transition(orderId, OrderStatus.READY_FOR_PICKUP, actorId, actorType, "READY_FOR_PICKUP", null);
    }

    /** IN_PROGRESS → OUT_FOR_DELIVERY: courier has picked up the order for delivery. */
    @Transactional
    public void markOutForDelivery(String orderId, String actorId, String actorType) {
        stateMachine.transition(orderId, OrderStatus.OUT_FOR_DELIVERY, actorId, actorType, "OUT_FOR_DELIVERY", null);
    }

    @Transactional
    public void markDelivered(String orderId, String actorId, String actorType) {
        stateMachine.transition(orderId, OrderStatus.DELIVERED, actorId, actorType, "DELIVERED", null);
    }

    /** COLLECTED | DELIVERED | ATTENDED → COMPLETED: closes the fulfilment lifecycle. */
    @Transactional
    public void completeOrder(String orderId, String actorId, String actorType) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getStatus() != OrderStatus.COLLECTED
                && order.getStatus() != OrderStatus.DELIVERED
                && order.getStatus() != OrderStatus.ATTENDED) {
            throw new IllegalStateException(
                    "Order must be COLLECTED, DELIVERED, or ATTENDED to complete. Current: " + order.getStatus());
        }
        stateMachine.transition(orderId, OrderStatus.COMPLETED, actorId, actorType, "ORDER_COMPLETED", null);
    }

    /**
     * Nhume dispatch-status callback handoff (see
     * {@code InternalFulfillmentController.dispatchStatus}). {@code PICKED_UP} maps to
     * out-for-delivery, {@code DELIVERED} to delivered; either way a custody record and
     * handoff event are appended carrying the Nhume delivery id / proof reference so
     * the chain-of-custody trail reflects the real courier event, not just the order
     * status flip.
     */
    @Transactional
    public void handleDispatchStatus(String orderId, String status, String nhumeDeliveryId,
                                     String proofRef, String actorId, String actorType) {
        switch (status) {
            case "PICKED_UP" -> markOutForDelivery(orderId, actorId, actorType);
            case "DELIVERED" -> markDelivered(orderId, actorId, actorType);
            default -> throw new IllegalArgumentException("Unsupported dispatch status: " + status);
        }
        recordDispatchCustody(orderId, status, nhumeDeliveryId, proofRef, actorId);
    }

    private void recordDispatchCustody(String orderId, String status, String nhumeDeliveryId,
                                       String proofRef, String actorId) {
        List<FulfillmentOrderEntity> fulfillments = fulfillmentOrderRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (fulfillments.isEmpty()) {
            log.warn("Dispatch status {} for order {} has no fulfillment record; custody/handoff skipped", status, orderId);
            return;
        }
        FulfillmentOrderEntity fulfillment = fulfillments.get(fulfillments.size() - 1);

        CustodyRecordEntity custody = new CustodyRecordEntity();
        custody.setCustodyId(UlidGenerator.generate());
        custody.setFulfillmentId(fulfillment.getFulfillmentId());
        custody.setCustodianType("NHUME_COURIER");
        custody.setCustodianRef(nhumeDeliveryId != null ? "nhume:" + nhumeDeliveryId : "nhume:unknown");
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("status", status);
        if (proofRef != null) condition.put("proofRef", proofRef);
        custody.setConditionSummaryJson(JsonSupport.toJsonSafe(objectMapper, condition, "{}"));
        custodyRecordRepository.save(custody);

        HandoffEventEntity handoff = new HandoffEventEntity();
        handoff.setHandoffId(UlidGenerator.generate());
        handoff.setFulfillmentId(fulfillment.getFulfillmentId());
        handoff.setHandoffType("NHUME_" + status);
        handoff.setFromPartyJson(JsonSupport.toJsonSafe(objectMapper,
                Map.of("system", "nhume", "deliveryId", nhumeDeliveryId != null ? nhumeDeliveryId : ""), "{}"));
        handoff.setToPartyJson(JsonSupport.toJsonSafe(objectMapper,
                Map.of("system", "msika-flow", "actorId", actorId != null ? actorId : ""), "{}"));
        handoff.setLocationJson("{}");
        Map<String, Object> meta = new LinkedHashMap<>();
        if (proofRef != null) meta.put("proofRef", proofRef);
        handoff.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, meta, "{}"));
        handoffEventRepository.save(handoff);
    }

    public List<FulfillmentRouteEntity> getStuckRoutes() {
        return routeRepository.findByStatusAndRetryCountLessThan(RouteStatus.FAILED, 5);
    }
}
