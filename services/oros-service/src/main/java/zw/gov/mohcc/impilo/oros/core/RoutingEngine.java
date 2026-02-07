package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.domain.*;
import zw.gov.mohcc.impilo.oros.persistence.entity.*;
import zw.gov.mohcc.impilo.oros.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Determines how to route an order based on facility capabilities.
 *
 * <p>Supports three routing modes:</p>
 * <ul>
 *   <li>INTERNAL -- order goes to the internal worklist for manual processing</li>
 *   <li>ADAPTER -- order is dispatched to an external system (LIMS, PACS, etc.)</li>
 *   <li>HYBRID -- order is sent both internally and externally, with reconciliation</li>
 * </ul>
 */
@Service
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);

    private final RoutingRepository routingRepository;
    private final CapabilityRepository capabilityRepository;
    private final ReconcileQueueRepository reconcileQueueRepository;
    private final EventOutboxRepository outboxRepository;
    private final OrosProperties properties;
    private final ObjectMapper objectMapper;

    public RoutingEngine(RoutingRepository routingRepository,
                         CapabilityRepository capabilityRepository,
                         ReconcileQueueRepository reconcileQueueRepository,
                         EventOutboxRepository outboxRepository,
                         OrosProperties properties,
                         ObjectMapper objectMapper) {
        this.routingRepository = routingRepository;
        this.capabilityRepository = capabilityRepository;
        this.reconcileQueueRepository = reconcileQueueRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Route an order based on the facility's capability configuration.
     * Falls back to tenant-level default if no facility-specific capability exists.
     */
    @Transactional
    public RoutingEntity routeOrder(OrderEntity order) {
        TrustContext ctx = TrustContextHolder.require();

        // Look up facility-specific capability first, then tenant default
        Optional<CapabilityEntity> capability = capabilityRepository
                .findByTenantIdAndFacilityIdAndOrderType(
                        ctx.tenantId(), order.getFacilityId(), order.getOrderType());

        if (capability.isEmpty()) {
            capability = capabilityRepository
                    .findByTenantIdAndFacilityIdIsNullAndOrderType(
                            ctx.tenantId(), order.getOrderType());
        }

        AdapterMode mode = capability.map(CapabilityEntity::getAdapterMode)
                .orElse(AdapterMode.valueOf(properties.getRouting().getDefaultMode()));

        RouteTarget target = resolveTarget(order.getOrderType(), mode);

        RoutingEntity route = new RoutingEntity();
        route.setRouteId(UUID.randomUUID());
        route.setOrderId(order.getOrderId());
        route.setRouteTarget(target);
        route.setAdapterMode(mode);

        switch (mode) {
            case INTERNAL -> {
                route.setStatus(RouteStatus.SENT);
                log.info("Order {} routed INTERNALLY to worklist", order.getOrderId());
            }
            case ADAPTER -> {
                route.setStatus(RouteStatus.PENDING);
                log.info("Order {} routed to ADAPTER target {}", order.getOrderId(), target);
            }
            case HYBRID -> {
                route.setStatus(RouteStatus.PENDING);
                // Create reconciliation entry for hybrid mode
                ReconcileQueueEntity reconcile = new ReconcileQueueEntity();
                reconcile.setTenantId(ctx.tenantId());
                reconcile.setOrderId(order.getOrderId());
                reconcile.setExternalKey(order.getOrderId());
                reconcile.setExternalSystem(target.name());
                reconcile.setPatientCpid(order.getPatientCpid());
                reconcile.setStatus(ReconcileStatus.PENDING);
                reconcile.setConfidence(BigDecimal.ZERO);
                reconcileQueueRepository.save(reconcile);
                log.info("Order {} routed HYBRID with reconcile entry", order.getOrderId());
            }
        }

        route = routingRepository.save(route);

        publishEvent("ROUTE", route.getRouteId().toString(),
                "ORDER_ROUTED", route, ctx.tenantId());

        return route;
    }

    /**
     * Retry routing for a failed order.
     */
    @Transactional
    public RoutingEntity retryRoute(String orderId) {
        TrustContext ctx = TrustContextHolder.require();

        List<RoutingEntity> routes = routingRepository.findByOrderId(orderId);
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("Route not found for order: " + orderId);
        }

        RoutingEntity route = routes.get(0);

        if (route.getStatus() != RouteStatus.FAILED) {
            throw new IllegalStateException("Cannot retry route in status " + route.getStatus());
        }

        route.setStatus(RouteStatus.RETRYING);
        route.setRetryCount(route.getRetryCount() + 1);
        route.setLastError(null);
        route = routingRepository.save(route);

        publishEvent("ROUTE", route.getRouteId().toString(),
                "ROUTE_RETRY", route, ctx.tenantId());

        log.info("Route retry initiated: orderId={}, attempt={}", orderId, route.getRetryCount());
        return route;
    }

    /**
     * Get the route for an order.
     */
    public RoutingEntity getRoute(String orderId) {
        List<RoutingEntity> routes = routingRepository.findByOrderId(orderId);
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("Route not found for order: " + orderId);
        }
        return routes.get(0);
    }

    private RouteTarget resolveTarget(OrderType orderType, AdapterMode mode) {
        if (mode == AdapterMode.INTERNAL) {
            return RouteTarget.INTERNAL;
        }
        return switch (orderType) {
            case LAB -> RouteTarget.LIMS;
            case IMAGING -> RouteTarget.PACS;
            case PHARMACY -> RouteTarget.PHARMACY_EXT;
            default -> RouteTarget.OTHER;
        };
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
