package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.IndawoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Coverage for {@link IndawoPlaceModeController} (GAP-2). Stateless composer over
 * the Indawo public-health surveillance SoR: happy-path wraps upstream data, and
 * every upstream failure degrades to 502 BAD_GATEWAY with an honest error code.
 *
 * <p>Follows the existing BFF controller-test convention (e.g.
 * {@code RegistryGeoLocalityControllerTest}): plain JUnit, controller invoked
 * directly, downstream client subclassed with overrides.</p>
 */
class IndawoPlaceModeControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summaryReturnsComposedDataOnSuccess() throws Exception {
        IndawoPlaceModeController controller = new IndawoPlaceModeController(new SuccessIndawoClient());

        ResponseEntity<Map<String, Object>> response = controller.summary("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
        assertNotNull(response.getBody().get("meta"));
        assertEquals("corr-1", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    @Test
    void summaryReturnsBadGatewayOnUpstreamFailure() {
        IndawoPlaceModeController controller = new IndawoPlaceModeController(new FailingIndawoClient());

        ResponseEntity<Map<String, Object>> response = controller.summary("req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PLACE_MODE_SUMMARY_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void alertsReturnsDataOnSuccess() throws Exception {
        IndawoPlaceModeController controller = new IndawoPlaceModeController(new SuccessIndawoClient());

        ResponseEntity<Map<String, Object>> response = controller.alerts("OPEN", "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void createAlertReturns201CreatedOnSuccess() throws Exception {
        IndawoPlaceModeController controller = new IndawoPlaceModeController(new SuccessIndawoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.createAlert(Map.of("kind", "FLOOD"), "req-4", "corr-4");

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void createAlertReturnsBadGatewayOnUpstreamFailure() {
        IndawoPlaceModeController controller = new IndawoPlaceModeController(new FailingIndawoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.createAlert(Map.of("kind", "FLOOD"), "req-5", "corr-5");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("ALERT_CREATE_FAILED",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    // ── stub clients ──

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class SuccessIndawoClient extends IndawoServiceClient {
        private SuccessIndawoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode placeModeSummary() {
            return node("""
                    {"openOutbreaks":2,"activeAlerts":5}
                    """);
        }

        @Override
        public JsonNode listSurveillance(String resource, String queryParam, String queryValue) {
            return node("""
                    [{"id":"a-1","status":"OPEN"}]
                    """);
        }

        @Override
        public JsonNode postSurveillance(String path, Map<String, Object> body) {
            return node("""
                    {"id":"a-99","status":"OPEN"}
                    """);
        }

        private static JsonNode node(String json) {
            try {
                return MAPPER.readTree(json);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class FailingIndawoClient extends IndawoServiceClient {
        private FailingIndawoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode placeModeSummary() {
            throw new RuntimeException("indawo unavailable");
        }

        @Override
        public JsonNode postSurveillance(String path, Map<String, Object> body) {
            throw new RuntimeException("indawo unavailable");
        }
    }
}
