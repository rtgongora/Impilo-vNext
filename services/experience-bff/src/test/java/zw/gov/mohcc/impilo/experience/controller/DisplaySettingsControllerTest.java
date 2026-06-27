package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.DataGovernanceServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisplaySettingsController} — a stateless proxy over the
 * data-governance display-preference store (G048). The controller no longer echoes/clamps;
 * it delegates to {@link DataGovernanceServiceClient} and flags unset prefs {@code persisted=false}.
 */
class DisplaySettingsControllerTest {

    private final DataGovernanceServiceClient client = mock(DataGovernanceServiceClient.class);
    private final DisplaySettingsController controller = new DisplaySettingsController(client);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void getRequiresActorId() {
        ResponseEntity<Map<String, Object>> res = controller.getDisplaySettings("req", "corr", null);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void getReturnsSafeDefaultsFlaggedNotPersistedWhenUnset() {
        when(client.getDisplaySettings("actor-1")).thenReturn(null);

        ResponseEntity<Map<String, Object>> res = controller.getDisplaySettings("req", "corr", "actor-1");

        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> attrs = attributes(res);
        assertEquals("system", attrs.get("theme"));
        assertEquals("medium", attrs.get("fontSize"));
        assertEquals("comfortable", attrs.get("density"));
        assertEquals(false, attrs.get("persisted"));
    }

    @Test
    void getReturnsPersistedValuesFromDataGovernance() throws Exception {
        JsonNode persisted = mapper.readTree(
                "{\"theme\":\"dark\",\"fontSize\":\"large\",\"density\":\"compact\"}");
        when(client.getDisplaySettings("actor-1")).thenReturn(persisted);

        Map<String, Object> attrs = attributes(controller.getDisplaySettings("req", "corr", "actor-1"));
        assertEquals("dark", attrs.get("theme"));
        assertEquals("large", attrs.get("fontSize"));
        assertEquals("compact", attrs.get("density"));
        assertEquals(true, attrs.get("persisted"));
    }

    @Test
    void updateRequiresActorId() {
        ResponseEntity<Map<String, Object>> res = controller.updateDisplaySettings(
                "req", "corr", null, Map.of("theme", "dark"));
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void updateProxiesToDataGovernanceAndReturnsPersistedValues() throws Exception {
        // The sovereign service persists authoritatively (and value-clamps); the BFF returns its result.
        JsonNode persisted = mapper.readTree(
                "{\"theme\":\"dark\",\"fontSize\":\"large\",\"density\":\"compact\"}");
        when(client.updateDisplaySettings(any(), eq("displaysettings-actor-1"))).thenReturn(persisted);

        ResponseEntity<Map<String, Object>> res = controller.updateDisplaySettings(
                "req", "corr", "actor-1", Map.of("theme", "dark", "fontSize", "large", "density", "compact"));

        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> attrs = attributes(res);
        assertEquals("dark", attrs.get("theme"));
        assertEquals("large", attrs.get("fontSize"));
        assertEquals("compact", attrs.get("density"));
        assertEquals(true, attrs.get("persisted"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(ResponseEntity<Map<String, Object>> res) {
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        return (Map<String, Object>) data.get("attributes");
    }
}
