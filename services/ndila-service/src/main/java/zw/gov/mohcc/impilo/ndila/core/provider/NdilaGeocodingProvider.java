package zw.gov.mohcc.impilo.ndila.core.provider;

import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;

import java.util.List;
import java.util.Optional;

public interface NdilaGeocodingProvider extends NdilaProviderAdapter {

    List<GeocodeResult> geocode(GeocodeRequest request);

    default Optional<GeocodeResult> reverseGeocode(Coordinate coordinate, String country) {
        return Optional.empty();
    }

    record GeocodeRequest(
            String query,
            String country,
            String province,
            String district,
            String purposeOfUse
    ) {}

    record GeocodeResult(
            String name,
            Coordinate coordinate,
            String address,
            String locality,
            String district,
            String province,
            String country,
            double confidence,
            String providerName,
            String providerPlaceId,
            String verificationStatus
    ) {}
}
