package zw.gov.mohcc.impilo.ndila.core.policy;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.adapter.MockProviderAdapter;
import zw.gov.mohcc.impilo.ndila.core.provider.adapter.OsmOsrmProviderAdapter;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NdilaProviderPolicyServiceTest {

    private NdilaProviderPolicyService policyDefaultMock() {
        MockProviderAdapter mock = new MockProviderAdapter();
        OsmOsrmProviderAdapter osm = new OsmOsrmProviderAdapter(
                false, "", false, "", true, "development");
        return new NdilaProviderPolicyService(
                List.<NdilaGeocodingProvider>of(mock),
                List.<NdilaRoutingProvider>of(mock, osm),
                List.<NdilaTileProvider>of(mock, osm),
                "MOCK", "MOCK", "MOCK", true, true);
    }

    @Test
    void defaultsToMockProviderForRouting() {
        NdilaProviderPolicyService policy = policyDefaultMock();
        NdilaOperationContext ctx = new NdilaOperationContext(
                UUID.randomUUID(), "actor-1", "PROVIDER",
                "OPERATIONS_MANAGEMENT", "DELIVERY", "dispatch-service",
                NdilaSensitivity.INTERNAL, false, false,
                UUID.randomUUID(), "development", "ZW");
        var decision = policy.selectRouting(ctx);
        assertThat(decision.chosenName()).isEqualTo("MOCK");
        assertThat(decision.decisionId()).startsWith("ndila-policy-");
    }

    @Test
    void sensitiveWorkflowPrefersInternalProviders() {
        NdilaProviderPolicyService policy = policyDefaultMock();
        NdilaOperationContext ctx = new NdilaOperationContext(
                UUID.randomUUID(), "actor-1", "RESPONDER",
                "EMERGENCY_RESPONSE", "EMERGENCY", "emergency-service",
                NdilaSensitivity.HIGHLY_RESTRICTED, false, true,
                UUID.randomUUID(), "development", "ZW");
        var decision = policy.selectRouting(ctx);
        assertThat(decision.sensitive()).isTrue();
        assertThat(decision.chosenName()).isEqualTo("MOCK");
    }

    @Test
    void productionEnvironmentExcludesPublicOsmTiles() {
        MockProviderAdapter mock = new MockProviderAdapter();
        OsmOsrmProviderAdapter publicOsm = new OsmOsrmProviderAdapter(
                false, "", true, "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                true, "production");
        NdilaProviderPolicyService policy = new NdilaProviderPolicyService(
                List.<NdilaGeocodingProvider>of(mock),
                List.<NdilaRoutingProvider>of(mock),
                List.<NdilaTileProvider>of(mock, publicOsm),
                "MOCK", "MOCK", "MOCK", true, true);
        NdilaOperationContext prodCtx = new NdilaOperationContext(
                UUID.randomUUID(), "actor", "OPS",
                "OPERATIONS_MANAGEMENT", "DASHBOARD", "ndila-api",
                NdilaSensitivity.PUBLIC, false, false,
                UUID.randomUUID(), "production", "ZW");
        var d = policy.selectTiles(prodCtx);
        assertThat(d.chosenName()).isEqualTo("MOCK");
        assertThat(d.fallbackChain()).doesNotContain("OSM_OSRM");
    }

    @Test
    void offlineModePrefersOfflineCapableProviders() {
        NdilaProviderPolicyService policy = policyDefaultMock();
        NdilaOperationContext ctx = new NdilaOperationContext(
                UUID.randomUUID(), "actor", "PROVIDER",
                "OPERATIONS_MANAGEMENT", "FIELD_INSPECTION", "field-app",
                NdilaSensitivity.INTERNAL, true, false,
                UUID.randomUUID(), "development", "ZW");
        var d = policy.selectRouting(ctx);
        assertThat(d.chosenName()).isEqualTo("MOCK");
    }
}
