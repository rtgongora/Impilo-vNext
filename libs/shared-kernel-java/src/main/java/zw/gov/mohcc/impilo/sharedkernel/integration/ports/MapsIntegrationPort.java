package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.util.List;

/**
 * Canonical maps/geospatial port. Adapters: DefaultMapProviderAdapter,
 * OpenStreetMapAdapter, GoogleMapsAdapter, MapboxAdapter, NativeNdilaAdapter.
 */
public interface MapsIntegrationPort extends Adapter {

    OperationResult<List<Place>>         geocode(String query, OperationContext ctx);
    OperationResult<Place>               reverseGeocode(double lat, double lon, OperationContext ctx);
    OperationResult<RouteSummary>        route(double fromLat, double fromLon, double toLat, double toLon, String mode, OperationContext ctx);

    record Place(String displayName, double lat, double lon, String confidence) {}
    record RouteSummary(double distanceMeters, double durationSeconds, String polyline) {}
}
