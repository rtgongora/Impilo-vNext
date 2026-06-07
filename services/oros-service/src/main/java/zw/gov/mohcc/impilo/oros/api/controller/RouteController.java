package zw.gov.mohcc.impilo.oros.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.core.RoutingEngine;
import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.RouteStatus;
import zw.gov.mohcc.impilo.oros.domain.RouteTarget;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * REST controller for order routing operations.
 *
 * <p>Provides endpoints for querying route status and retrying
 * failed routes.</p>
 */
@RestController
@RequestMapping("/v1/routes")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RoutingEngine routingEngine;

    public RouteController(RoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
    }

    /**
     * Get the route for an order.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<RouteResponse>> getRoute(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        RoutingEntity route = routingEngine.getRoute(orderId);
        RouteResponse response = RouteResponse.from(route);

        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Retry a failed route (order-scoped legacy path).
     */
    @PostMapping("/retry/{orderId}")
    public ResponseEntity<ApiResponse<RouteResponse>> retryRoute(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        RoutingEntity route = routingEngine.retryRoute(orderId);
        RouteResponse response = RouteResponse.from(route);

        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Contract-aligned retry by route UUID.
     */
    @PostMapping("/{routeId}/retry")
    public ResponseEntity<ApiResponse<RouteResponse>> retryRouteById(@PathVariable UUID routeId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        RoutingEntity route = routingEngine.retryRouteByRouteId(routeId);
        RouteResponse response = RouteResponse.from(route);

        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Response DTO for route data.
     */
    record RouteResponse(UUID routeId, String orderId, RouteTarget routeTarget,
                         AdapterMode adapterMode, RouteStatus status,
                         String lastError, int retryCount, String externalRef) {
        static RouteResponse from(RoutingEntity entity) {
            return new RouteResponse(
                    entity.getRouteId(), entity.getOrderId(), entity.getRouteTarget(),
                    entity.getAdapterMode(), entity.getStatus(),
                    entity.getLastError(), entity.getRetryCount(), entity.getExternalRef());
        }
    }
}
