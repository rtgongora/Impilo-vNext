package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NdilaServiceClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rewriteTileConfigUsesBrowserRelativePathForPreviewSovereign() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("providerName", "PREVIEW_SOVEREIGN");
        node.put("tileUrlTemplate", "/api/v1/ndila/tiles/{z}/{x}/{y}.png");

        var rewritten = NdilaServiceClient.rewriteTileConfigForBrowser(node);

        assertEquals("/internal/v1/ndila/tiles/{z}/{x}/{y}.png", rewritten.get("tileUrlTemplate").asText());
    }

    @Test
    void rewriteTileConfigMapsVectorTemplateToBrowserRelativePath() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("providerName", "OSM_OSRM");
        node.put("tileUrlTemplate", "/api/v1/ndila/tiles/{z}/{x}/{y}.png");
        node.put("vectorTileUrlTemplate", "/api/v1/ndila/tiles/{z}/{x}/{y}.mvt");

        var rewritten = NdilaServiceClient.rewriteTileConfigForBrowser(node);

        assertEquals("/internal/v1/ndila/tiles/{z}/{x}/{y}.mvt", rewritten.get("vectorTileUrlTemplate").asText());
        assertEquals("/internal/v1/ndila/tiles/{z}/{x}/{y}.png", rewritten.get("tileUrlTemplate").asText());
    }

    @Test
    void rewriteTileConfigLeavesVectorAbsentWhenStreetStackInactive() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("providerName", "PREVIEW_SOVEREIGN");
        node.put("tileUrlTemplate", "/api/v1/ndila/tiles/{z}/{x}/{y}.png");

        var rewritten = NdilaServiceClient.rewriteTileConfigForBrowser(node);

        assertTrue(rewritten.path("vectorTileUrlTemplate").isMissingNode());
    }

    @Test
    void rewriteTileConfigUpgradesMockTemplates() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("providerName", "MOCK");
        node.put("tileUrlTemplate", "mock://tiles/{z}/{x}/{y}.png");

        var rewritten = NdilaServiceClient.rewriteTileConfigForBrowser(node);

        assertEquals("PREVIEW_SOVEREIGN", rewritten.get("providerName").asText());
        assertTrue(rewritten.get("tileUrlTemplate").asText().startsWith("/internal/v1/ndila/tiles/"));
    }
}
