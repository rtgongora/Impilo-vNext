package zw.gov.mohcc.impilo.ndila.core.provider;

import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;

import java.util.List;

public interface NdilaIsochroneProvider extends NdilaProviderAdapter {

    IsochroneResult isochrone(IsochroneRequest request);

    record IsochroneRequest(Coordinate origin, int minutes, String mode) {}

    record IsochroneResult(
            String providerName,
            String mode,
            int minutes,
            /** GeoJSON polygon serialized as String. */
            String geometryJson,
            List<String> warnings,
            double confidence,
            boolean fallbackUsed
    ) {}
}
