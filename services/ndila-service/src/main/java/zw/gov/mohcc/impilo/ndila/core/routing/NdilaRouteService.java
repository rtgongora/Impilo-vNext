package zw.gov.mohcc.impilo.ndila.core.routing;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaEventTopics;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaProviderPolicyService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaTshepoGuard;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaProviderAdapter;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NdilaRouteService {

    private final NdilaProviderPolicyService policy;
    private final NdilaTshepoGuard tshepoGuard;
    private final NdilaOutboxPublisher outbox;

    public NdilaRouteService(NdilaProviderPolicyService policy,
                             NdilaTshepoGuard tshepoGuard,
                             NdilaOutboxPublisher outbox) {
        this.policy = policy;
        this.tshepoGuard = tshepoGuard;
        this.outbox = outbox;
    }

    public RouteResponse route(NdilaOperationContext ctx, Coordinate origin, Coordinate destination,
                               String mode, String optimizeFor, List<String> avoid, boolean includeGeometry) {
        NdilaTshepoGuard.Decision authz = tshepoGuard.authorize(ctx, "route");
        if (!authz.allowed()) {
            return RouteResponse.denied(authz.reason());
        }
        NdilaProviderPolicyService.Decision decision = policy.selectRouting(ctx);
        publishProviderSelected(decision, ctx);

        NdilaRoutingProvider.RouteRequest req = new NdilaRoutingProvider.RouteRequest(
                origin, destination, mode == null ? "CAR" : mode,
                optimizeFor == null ? "FASTEST" : optimizeFor,
                avoid == null ? List.of() : avoid,
                includeGeometry);

        NdilaProviderAdapter chosen = decision.chosen();
        NdilaRoutingProvider.RouteResult result = null;
        if (chosen instanceof NdilaRoutingProvider rp) {
            result = rp.route(req);
        }
        if (result == null) {
            return RouteResponse.denied("NO_PROVIDER_AVAILABLE");
        }
        publishRouteCalculated(ctx, decision, result);

        return new RouteResponse(true, null,
                result.distanceMeters(), result.durationSeconds(),
                result.mode(), result.providerName(), result.providerMode(),
                result.fallbackUsed(), result.servedFromCache(),
                result.polyline(), result.warnings(), result.confidence(),
                decision.decisionId());
    }

    public MultiStopResponse multiStop(NdilaOperationContext ctx, List<Coordinate> waypoints,
                                       String mode, String optimizeFor) {
        if (waypoints == null || waypoints.size() < 2) {
            return new MultiStopResponse(false, "NEED_AT_LEAST_TWO_WAYPOINTS",
                    List.of(), 0, 0, null, null);
        }
        double totalDistance = 0, totalDuration = 0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            RouteResponse leg = route(ctx, waypoints.get(i), waypoints.get(i + 1),
                    mode, optimizeFor, List.of(), false);
            if (!leg.success()) {
                return new MultiStopResponse(false, leg.denialReason(),
                        List.of(), 0, 0, null, null);
            }
            totalDistance += leg.distanceMeters();
            totalDuration += leg.durationSeconds();
        }
        return new MultiStopResponse(true, null, waypoints, totalDistance, totalDuration, mode,
                "multi-stop");
    }

    public DistanceMatrixResponse distanceMatrix(NdilaOperationContext ctx,
                                                 List<Coordinate> origins,
                                                 List<Coordinate> destinations,
                                                 String mode) {
        NdilaTshepoGuard.Decision authz = tshepoGuard.authorize(ctx, "distance-matrix");
        if (!authz.allowed()) {
            return new DistanceMatrixResponse(false, authz.reason(),
                    new double[0][0], new double[0][0], null);
        }
        NdilaProviderPolicyService.Decision decision = policy.selectRouting(ctx);
        publishProviderSelected(decision, ctx);
        NdilaRoutingProvider.DistanceMatrixResult result;
        if (decision.chosen() instanceof NdilaRoutingProvider rp) {
            result = rp.distanceMatrix(new NdilaRoutingProvider.DistanceMatrixRequest(
                    origins, destinations, mode == null ? "CAR" : mode));
        } else {
            return new DistanceMatrixResponse(false, "NO_PROVIDER_AVAILABLE",
                    new double[0][0], new double[0][0], null);
        }
        return new DistanceMatrixResponse(true, null,
                result.distancesMeters(), result.durationsSeconds(), result.providerName());
    }

    private void publishProviderSelected(NdilaProviderPolicyService.Decision d, NdilaOperationContext ctx) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operation", d.operationType());
        payload.put("provider", d.chosenName());
        payload.put("fallbackChain", d.fallbackChain());
        payload.put("reason", d.reason());
        payload.put("decisionId", d.decisionId());
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.PROVIDER_SELECTED, "ProviderDecision", d.decisionId(),
                ctx.tenantId(), ctx.correlationId(), payload));
    }

    private void publishRouteCalculated(NdilaOperationContext ctx,
                                        NdilaProviderPolicyService.Decision d,
                                        NdilaRoutingProvider.RouteResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("provider", result.providerName());
        payload.put("mode", result.mode());
        payload.put("distanceMeters", result.distanceMeters());
        payload.put("durationSeconds", result.durationSeconds());
        payload.put("fallbackUsed", result.fallbackUsed());
        payload.put("decisionId", d.decisionId());
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.ROUTE_CALCULATED, "Route", d.decisionId(),
                ctx.tenantId(), ctx.correlationId(), payload));
    }

    public record RouteResponse(
            boolean success,
            String denialReason,
            double distanceMeters,
            double durationSeconds,
            String mode,
            String provider,
            String providerMode,
            boolean fallbackUsed,
            boolean servedFromCache,
            String polyline,
            List<String> warnings,
            double confidence,
            String policyDecisionId
    ) {
        public static RouteResponse denied(String reason) {
            return new RouteResponse(false, reason, 0, 0, null, null, null,
                    false, false, null, List.of(), 0.0, null);
        }
    }

    public record MultiStopResponse(
            boolean success,
            String denialReason,
            List<Coordinate> waypoints,
            double totalDistanceMeters,
            double totalDurationSeconds,
            String mode,
            String provider
    ) {}

    public record DistanceMatrixResponse(
            boolean success,
            String denialReason,
            double[][] distancesMeters,
            double[][] durationsSeconds,
            String provider
    ) {}
}
