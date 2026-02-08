package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
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
    private final CapabilityProfileRepository capabilityRepository;
    private final ObjectMapper objectMapper;

    public FulfillmentService(OrderStateMachine stateMachine,
                              FulfillmentRouteRepository routeRepository,
                              ReservationRepository reservationRepository,
                              OrderLineRepository orderLineRepository,
                              CapabilityProfileRepository capabilityRepository,
                              ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.routeRepository = routeRepository;
        this.reservationRepository = reservationRepository;
        this.orderLineRepository = orderLineRepository;
        this.capabilityRepository = capabilityRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FulfillmentRouteEntity routeOrder(String orderId, String actorId, String actorType) {
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

        // Create reservations for product lines
        for (OrderLineEntity line : lines) {
            if (line.getKind() == LineItemKind.PRODUCT) {
                createReservation(orderId, line);
            }
        }

        stateMachine.transition(orderId, OrderStatus.ROUTED, actorId, actorType, "ORDER_ROUTED", "Route: " + mode);
        log.info("Order routed: id={} mode={}", orderId, mode);
        return route;
    }

    private FulfillmentMode determineRoute(OrderEntity order, List<OrderLineEntity> lines) {
        // Check if any line has explicit fulfillment mode
        for (OrderLineEntity line : lines) {
            if (line.getFulfillmentMode() != null) {
                return line.getFulfillmentMode();
            }
        }

        // Check capability profile for tenant/facility
        if (order.getFacilityId() != null) {
            Optional<CapabilityProfileEntity> cap = capabilityRepository
                    .findByTenantIdAndFacilityId(order.getTenantId(), order.getFacilityId());
            if (cap.isPresent()) {
                // Parse profile for default fulfillment mode
                // For now, default to PHARMACY_PICKUP for product orders
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

    @Transactional
    public void markDelivered(String orderId, String actorId, String actorType) {
        stateMachine.transition(orderId, OrderStatus.DELIVERED, actorId, actorType, "DELIVERED", null);
    }

    public List<FulfillmentRouteEntity> getStuckRoutes() {
        return routeRepository.findByStatusAndRetryCountLessThan(RouteStatus.FAILED, 5);
    }
}
