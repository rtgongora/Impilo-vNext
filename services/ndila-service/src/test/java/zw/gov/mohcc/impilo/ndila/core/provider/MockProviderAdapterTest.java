package zw.gov.mohcc.impilo.ndila.core.provider;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.provider.adapter.MockProviderAdapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockProviderAdapterTest {

    private final MockProviderAdapter mock = new MockProviderAdapter();

    @Test
    void mockProviderIsAlwaysEnabledAndProductionSafe() {
        assertThat(mock.enabled()).isTrue();
        assertThat(mock.productionSafe()).isTrue();
        assertThat(mock.providerName()).isEqualTo("MOCK");
    }

    @Test
    void mockGeocodeIsDeterministicForSameQuery() {
        var req = new NdilaGeocodingProvider.GeocodeRequest("Parirenyatwa Hospital", "ZWE", "Harare", "Harare", "OPS");
        var first = mock.geocode(req);
        var again = mock.geocode(req);
        assertThat(first).hasSize(1);
        assertThat(again).hasSize(1);
        assertThat(first.get(0).providerPlaceId()).isEqualTo(again.get(0).providerPlaceId());
        assertThat(first.get(0).providerName()).isEqualTo("MOCK");
        assertThat(first.get(0).confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void mockRouteReturnsApproximateDistanceAndDurationWithWarnings() {
        Coordinate harare = new Coordinate(-17.8292, 31.0522);
        Coordinate chitungwiza = new Coordinate(-18.0125, 31.0750);
        var req = new NdilaRoutingProvider.RouteRequest(harare, chitungwiza, "CAR", "FASTEST", List.of(), false);
        var r = mock.route(req);
        assertThat(r.distanceMeters()).isGreaterThan(0);
        assertThat(r.durationSeconds()).isGreaterThan(0);
        assertThat(r.providerName()).isEqualTo("MOCK");
        assertThat(r.warnings()).isNotEmpty();
    }

    @Test
    void mockDroneRouteEmitsDronePlaceholderWarning() {
        Coordinate origin = new Coordinate(-17.83, 31.05);
        Coordinate target = new Coordinate(-17.84, 31.06);
        var req = new NdilaRoutingProvider.RouteRequest(origin, target, "DRONE", "FASTEST", List.of(), false);
        var r = mock.route(req);
        assertThat(r.warnings().stream().anyMatch(w -> w.toLowerCase().contains("drone"))).isTrue();
    }

    @Test
    void mockReverseGeocodeReturnsApproximateAddress() {
        var rev = mock.reverseGeocode(new Coordinate(-17.83, 31.05), "ZWE");
        assertThat(rev).isPresent();
        assertThat(rev.get().providerName()).isEqualTo("MOCK");
    }

    @Test
    void mockTileConfigIsSelfContainedAndOfflineCapable() {
        var cfg = mock.tileConfig(new NdilaTileProvider.TileConfigRequest("zw-mohcc", "development", "general"));
        assertThat(cfg.tileUrlTemplate()).startsWith("mock://");
        assertThat(cfg.supportsOffline()).isTrue();
    }

    @Test
    void mockCapabilitiesAdvertiseSensitivitySafety() {
        var caps = mock.capabilities();
        assertThat(caps.supportsSensitiveWorkflows()).isTrue();
        assertThat(caps.estimatedCostCategory()).isEqualTo("FREE");
        assertThat(caps.dataResidencyNotes()).contains("In-process");
    }
}
