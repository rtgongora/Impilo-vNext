package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DataGovernanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.publichealth.PublicHealthGovernanceService;
import zw.gov.mohcc.impilo.experience.publichealth.PublicHealthPreferenceService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicHealthControllerTest {

    private static PublicHealthGovernanceService permissiveGovernance() {
        PublicHealthGovernanceService governance = new PublicHealthGovernanceService(
                Mockito.mock(TshepoAuthzServiceClient.class),
                Mockito.mock(TshepoAuditServiceClient.class));
        ReflectionTestUtils.setField(governance, "allowAnonymous", true);
        return governance;
    }

    private static PublicHealthController controller(RestTemplate restTemplate) {
        return new PublicHealthController(
                restTemplate,
                ServiceClientConfig.testServiceEndpoints(),
                permissiveGovernance(),
                Mockito.mock(PublicHealthPreferenceService.class),
                Mockito.mock(DataGovernanceServiceClient.class));
    }

    @Test
    void listSitesReturnsBadGatewayWhenIndawoUnavailable() {
        PublicHealthController controller = controller(new ThrowingRestTemplate());

        var response = controller.listSites("req-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void weeklyIdsrFailsClosedWhenSurveillanceUnavailable() {
        PublicHealthController controller = controller(new ThrowingRestTemplate());

        var response = controller.weeklyIdsr("req-2", null, null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createOutbreakFailsClosedWhenSurveillanceUnavailable() {
        PublicHealthController controller = controller(new ThrowingRestTemplate());

        var response = controller.createOutbreak("req-3", Map.of("name", "cholera-cluster"));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PUBLIC_HEALTH_UPSTREAM_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void weeklyIdsrUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        var response = controller.weeklyIdsr("req-4", "2026-05-01", "2026-05-08");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastGetUrl);
        org.junit.jupiter.api.Assertions.assertTrue(restTemplate.lastGetUrl.contains("/internal/v1/public-health/weekly-idsr"));
    }

    @Test
    void createOutbreakUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        var response = controller.createOutbreak("req-5", Map.of("name", "cluster"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(restTemplate.lastPostUrl);
        org.junit.jupiter.api.Assertions.assertTrue(restTemplate.lastPostUrl.contains("/internal/v1/public-health/outbreaks"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOutbreakNormalizesSeverityAndSnakeCaseFields() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        controller.createOutbreak("req-8", Map.of(
                "name", "Harare cluster",
                "event_type", "SUSPECTED_OUTBREAK",
                "condition_field", "condition_code",
                "severity", "GRADE_3"
        ));

        assertNotNull(restTemplate.lastPostBody);
        Map<String, Object> payload = (Map<String, Object>) restTemplate.lastPostBody;
        assertEquals("SUSPECTED_OUTBREAK", payload.get("eventType"));
        assertEquals("condition_code", payload.get("conditionField"));
        assertEquals("EMERGENCY", payload.get("severity"));
    }

    @Test
    void createFieldOperationUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        var response = controller.createFieldOperation("req-6", Map.of("facility_id", "9b2ec8af-5e6b-4016-a336-c7bc132614f5"));

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(restTemplate.lastPostUrl);
        assertTrue(restTemplate.lastPostUrl.contains("/internal/v1/public-health/field-operations"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createFieldOperationNormalizesCamelCaseFallbackFields() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        controller.createFieldOperation("req-7", Map.of(
                "linkedSiteId", "site-123",
                "activityType", "CONTACT_TRACING",
                "activityDate", "2026-05-16",
                "status", "IN_PROGRESS"
        ));

        assertNotNull(restTemplate.lastPostBody);
        Map<String, Object> payload = (Map<String, Object>) restTemplate.lastPostBody;
        assertEquals("site-123", payload.get("facilityId"));
        assertEquals("CONTACT_TRACING", payload.get("operationType"));
        assertEquals("2026-05-16", payload.get("occurredOn"));
    }

    @Test
    void acknowledgeAlertUsesSurveillanceAlertsEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        var response = controller.acknowledgeAlert("123", "req-9");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastPostUrl);
        assertTrue(restTemplate.lastPostUrl.contains("/internal/v1/surveillance/alerts/123/acknowledge"));
    }

    @Test
    void exportBriefUsesDedicatedSovereignEndpoint() {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PublicHealthController controller = controller(restTemplate);

        var response = controller.exportBrief(
                "77",
                "00000000-0000-4000-8000-000000000001",
                "req-10",
                "corr-10",
                "PUBLIC_HEALTH",
                null,
                "actor-1",
                Map.of("format", "CSV"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(restTemplate.lastPostUrl);
        assertTrue(restTemplate.lastPostUrl.contains("/internal/v1/public-health/reports/briefs/77/exports"));
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
        Object lastPostBody;

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
            this.lastPostBody = request;
            @SuppressWarnings("unchecked")
            T body = (T) com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            return ResponseEntity.ok(body);
        }
    }
}
