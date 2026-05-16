package zw.gov.mohcc.impilo.ndila.core.provider;

import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;

import java.util.List;

public interface NdilaRoutingProvider extends NdilaProviderAdapter {

    RouteResult route(RouteRequest request);

    default RouteResult multiStop(MultiStopRouteRequest request) {
        return route(new RouteRequest(
                request.waypoints().get(0),
                request.waypoints().get(request.waypoints().size() - 1),
                request.mode(),
                request.optimizeFor(),
                request.avoid(),
                request.includeGeometry()));
    }

    default DistanceMatrixResult distanceMatrix(DistanceMatrixRequest request) {
        // Default implementation: pairwise route() calls. Concrete adapters can
        // override for efficiency / native distance-matrix endpoints.
        int rows = request.origins().size();
        int cols = request.destinations().size();
        double[][] distances = new double[rows][cols];
        double[][] durations = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                RouteResult r = route(new RouteRequest(
                        request.origins().get(i),
                        request.destinations().get(j),
                        request.mode(),
                        "FASTEST",
                        List.of(),
                        false));
                distances[i][j] = r.distanceMeters();
                durations[i][j] = r.durationSeconds();
            }
        }
        return new DistanceMatrixResult(distances, durations, providerName(), false, false);
    }

    record RouteRequest(
            Coordinate origin,
            Coordinate destination,
            String mode,
            String optimizeFor,
            List<String> avoid,
            boolean includeGeometry
    ) {}

    record MultiStopRouteRequest(
            List<Coordinate> waypoints,
            String mode,
            String optimizeFor,
            List<String> avoid,
            boolean includeGeometry
    ) {}

    record DistanceMatrixRequest(
            List<Coordinate> origins,
            List<Coordinate> destinations,
            String mode
    ) {}

    record RouteResult(
            double distanceMeters,
            double durationSeconds,
            String mode,
            String providerName,
            String providerMode,
            boolean fallbackUsed,
            boolean servedFromCache,
            String polyline,
            List<String> warnings,
            double confidence
    ) {}

    record DistanceMatrixResult(
            double[][] distancesMeters,
            double[][] durationsSeconds,
            String providerName,
            boolean fallbackUsed,
            boolean servedFromCache
    ) {}
}
