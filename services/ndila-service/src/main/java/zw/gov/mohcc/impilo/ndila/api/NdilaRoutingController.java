package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.routing.NdilaRouteService;

import java.util.List;

@RestController
public class NdilaRoutingController {

    private final NdilaRouteService routeService;

    public NdilaRoutingController(NdilaRouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping({"/api/v1/ndila/routes", "/api/v1/maps/routes"})
    public ResponseEntity<RouteResponse> route(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody RouteRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType, purpose, "ROUTING", "ndila-api",
                "INTERNAL", correlationId, "development", "ZW");
        Coordinate origin = toCoord(req.origin());
        Coordinate destination = toCoord(req.destination());
        NdilaRouteService.RouteResponse r = routeService.route(
                ctx, origin, destination, req.mode(), req.optimizeFor(),
                req.avoid(), req.includeGeometry());
        return ResponseEntity.ok(new RouteResponse(
                r.success(), r.denialReason(), r.distanceMeters(), r.durationSeconds(),
                r.mode(), r.provider(), r.providerMode(), r.fallbackUsed(),
                r.servedFromCache(), r.polyline(), r.warnings(), r.confidence(),
                r.policyDecisionId()));
    }

    @PostMapping({"/api/v1/ndila/routes/multi-stop", "/api/v1/maps/routes/multi-stop"})
    public ResponseEntity<NdilaRouteService.MultiStopResponse> multiStop(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody MultiStopRouteRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType, purpose, "ROUTING_MULTI_STOP",
                "ndila-api", "INTERNAL", correlationId, "development", "ZW");
        List<Coordinate> waypoints = req.waypoints().stream().map(this::toCoord).toList();
        return ResponseEntity.ok(routeService.multiStop(ctx, waypoints, req.mode(), req.optimizeFor()));
    }

    @PostMapping({"/api/v1/ndila/distance-matrix", "/api/v1/maps/distance-matrix"})
    public ResponseEntity<DistanceMatrixResponse> distanceMatrix(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody DistanceMatrixRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType, purpose, "DISTANCE_MATRIX",
                "ndila-api", "INTERNAL", correlationId, "development", "ZW");
        List<Coordinate> origins = req.origins().stream().map(this::toCoord).toList();
        List<Coordinate> destinations = req.destinations().stream().map(this::toCoord).toList();
        NdilaRouteService.DistanceMatrixResponse r = routeService.distanceMatrix(
                ctx, origins, destinations, req.mode());
        return ResponseEntity.ok(new DistanceMatrixResponse(
                r.success(), r.denialReason(), r.distancesMeters(), r.durationsSeconds(), r.provider()));
    }

    @PostMapping({"/api/v1/ndila/eta", "/api/v1/maps/eta"})
    public ResponseEntity<RouteResponse> eta(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody RouteRequest req) {
        // ETA is a route call without geometry. The caller's actor identity has to travel with it:
        // these operations are INTERNAL, and the Tshepo guard denies INTERNAL outright when the
        // actor is absent — so a handler that drops the actor headers can never be authorized.
        return route(tenantId, actorId, actorType, purpose, correlationId,
                new RouteRequest(req.origin(), req.destination(), req.mode(), req.optimizeFor(),
                        req.avoid(), false));
    }

    private Coordinate toCoord(CoordinateDto d) {
        return new Coordinate(d.latitude().doubleValue(), d.longitude().doubleValue());
    }
}
