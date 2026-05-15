package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicHealthControllerTest {

    @Test
    void listSitesReturnsBadGatewayWhenIndawoUnavailable() {
        PublicHealthController controller = new PublicHealthController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.listSites("req-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void weeklyIdsrFailsClosedWhenSurveillanceUnavailable() {
        PublicHealthController controller = new PublicHealthController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.weeklyIdsr("req-2", null, null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createOutbreakFailsClosedWhenSurveillanceUnavailable() {
        PublicHealthController controller = new PublicHealthController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.createOutbreak("req-3", Map.of("name", "cholera-cluster"));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void weeklyIdsrUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = new PublicHealthController(restTemplate, ServiceClientConfig.testServiceEndpoints());

        var response = controller.weeklyIdsr("req-4", "2026-05-01", "2026-05-08");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        org.junit.jupiter.api.Assertions.assertTrue(restTemplate.lastGetUrl.contains("/internal/v1/public-health/weekly-idsr"));
    }

    @Test
    void createOutbreakUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = new PublicHealthController(restTemplate, ServiceClientConfig.testServiceEndpoints());

        var response = controller.createOutbreak("req-5", Map.of("name", "cluster"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(restTemplate.lastPostUrl);
        org.junit.jupiter.api.Assertions.assertTrue(restTemplate.lastPostUrl.contains("/internal/v1/public-health/outbreaks"));
    }

    @Test
    void createFieldOperationUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = new PublicHealthController(restTemplate, ServiceClientConfig.testServiceEndpoints());

        var response = controller.createFieldOperation("req-6", Map.of("facility_id", "9b2ec8af-5e6b-4016-a336-c7bc132614f5"));

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(restTemplate.lastPostUrl);
        org.junit.jupiter.api.Assertions.assertTrue(restTemplate.lastPostUrl.contains("/internal/v1/public-health/field-operations"));
    }

    private static final class ThrowingRestTemplate extends RestTemplate {
        @Override
        public <T> org.springframework.http.ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            throw new RuntimeException("upstream unavailable");
        }

        @Override
        public <T> org.springframework.http.ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
            throw new RuntimeException("upstream unavailable");
        }
    }

    private static final class CapturingRestTemplate extends RestTemplate {
        String lastGetUrl;
        String lastPostUrl;

        @Override
        public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            this.lastGetUrl = url;
            @SuppressWarnings("unchecked")
            T body = (T) com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            return ResponseEntity.ok(body);
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
            this.lastPostUrl = url;
            @SuppressWarnings("unchecked")
            T body = (T) com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            return ResponseEntity.ok(body);
        }
    }
}
