package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmOsrmProviderAdapterTileConfigTest {

    @Test
    void tileConfigUsesNdilaProxyPathWhenOsmEnabled() {
        OsmOsrmProviderAdapter adapter = new OsmOsrmProviderAdapter(
                null,
                false,
                "",
                true,
                "http://ndila-martin:3000/zimbabwe/{z}/{x}/{y}",
                true,
                "/api/v1/ndila",
                true,
                "staging");

        assertTrue(adapter.enabled());
        NdilaTileProvider.TileConfig cfg = adapter.tileConfig(
                new NdilaTileProvider.TileConfigRequest("zw-mohcc", "staging", "general"));
        assertEquals("/api/v1/ndila/tiles/{z}/{x}/{y}.png", cfg.tileUrlTemplate());
        assertEquals(OsmOsrmProviderAdapter.NAME, cfg.providerName());
    }
}
