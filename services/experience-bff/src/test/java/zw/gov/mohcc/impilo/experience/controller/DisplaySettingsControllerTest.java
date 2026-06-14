package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySettingsControllerTest {

    private final DisplaySettingsController controller = new DisplaySettingsController();

    @Test
    void getRequiresActorId() {
        ResponseEntity<Map<String, Object>> res = controller.getDisplaySettings("req", "corr", null);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void getReturnsDefaults() {
        ResponseEntity<Map<String, Object>> res = controller.getDisplaySettings("req", "corr", "actor-1");
        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        assertEquals("system", attrs.get("theme"));
        assertEquals("medium", attrs.get("fontSize"));
        assertEquals("comfortable", attrs.get("density"));
    }

    @Test
    void putNormalizesInvalidValues() {
        ResponseEntity<Map<String, Object>> res = controller.updateDisplaySettings(
                "req", "corr", "actor-1",
                Map.of("theme", "invalid", "fontSize", "large", "density", "compact"));
        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) ((Map<?, ?>) res.getBody().get("data")).get("attributes");
        assertEquals("system", attrs.get("theme"));
        assertEquals("large", attrs.get("fontSize"));
        assertEquals("compact", attrs.get("density"));
    }
}
