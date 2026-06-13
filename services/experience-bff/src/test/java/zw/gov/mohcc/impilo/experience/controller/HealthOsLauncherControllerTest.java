package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.MsikaAppsClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.service.HealthOsLauncherCatalogService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthOsLauncherControllerTest {

    private final HealthOsLauncherController controller = new HealthOsLauncherController(
            new StubMsikaAppsClient(),
            new ObjectMapper(),
            new HealthOsLauncherCatalogService(new ObjectMapper()));

    @Test
    void listApps_withoutFacilityId_returnsRegistryCatalog() {
        ResponseEntity<Map<String, Object>> response = controller.listApps(null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("", ((Map<?, ?>) body.get("actor")).get("facilityId"));
        List<?> apps = (List<?>) body.get("apps");
        assertTrue(apps.size() >= 31);
        assertEquals(31, body.get("catalogCount"));
        assertTrue(apps.stream().anyMatch(app -> "registry".equals(((Map<?, ?>) app).get("appCode"))));
    }

    @Test
    void listApps_withFacilityId_returns200AndPreservesActorContext() {
        ResponseEntity<Map<String, Object>> response =
                controller.listApps(null, null, "FAC-SEED-001", "CLINICAL");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("FAC-SEED-001", ((Map<?, ?>) body.get("actor")).get("facilityId"));
        List<?> apps = (List<?>) body.get("apps");
        assertFalse(apps.isEmpty());
    }

    @Test
    void listApps_includesReadinessMetadataOnCatalogTiles() {
        ResponseEntity<Map<String, Object>> response = controller.listApps(null, null, null, null);
        List<?> apps = (List<?>) response.getBody().get("apps");
        @SuppressWarnings("unchecked")
        Map<String, Object> registry = (Map<String, Object>) apps.stream()
                .filter(app -> "registry".equals(((Map<?, ?>) app).get("appCode")))
                .findFirst()
                .orElseThrow();
        assertEquals("registry", registry.get("plane"));
        assertEquals("/registry", registry.get("route"));
        assertEquals(true, registry.get("apiBacked"));
        assertEquals("ready", registry.get("readiness"));
    }

    private static final class StubMsikaAppsClient extends MsikaAppsClient {
        StubMsikaAppsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public ResponseEntity<String> launcher(String facilityId, String roles) {
            return ResponseEntity.ok("[]");
        }

        @Override
        public ResponseEntity<String> catalogue(org.springframework.util.MultiValueMap<String, String> query) {
            return ResponseEntity.ok("[]");
        }
    }
}
