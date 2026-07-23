package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplate;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTemplateControllerTest {

    private final SessionTemplateController controller =
            new SessionTemplateController(new SessionTemplateRegistry());

    @Test
    void get_returnsTelemedicineTemplate() {
        ResponseEntity<Map<String, Object>> response = controller.get("telemedicine", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("session-template", data.get("type"));
        SessionTemplate template = (SessionTemplate) data.get("attributes");
        assertEquals("PCT", template.owningService());
        assertNotNull(template.grantFor("PROVIDER"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void get_returns404ForUnknownMode() {
        ResponseEntity<Map<String, Object>> response = controller.get("KARAOKE", "req-2", "corr-2");

        assertEquals(404, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("SESSION_MODE_UNKNOWN", error.get("code"));
    }

    @Test
    void list_returnsAllRegisteredModes() {
        ResponseEntity<Map<String, Object>> response = controller.list("req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<String> modes = (List<String>) ((Map<?, ?>) data.get("attributes")).get("modes");
        // Six modes since TM-B15 added MDT (chaired specialist-board review).
        assertEquals(6, modes.size());
        assertTrue(modes.containsAll(List.of(
                "MEETING", "LIVE_EVENT", "LEARNING_LIVE", "LEARNING_RECORDING", "TELEMEDICINE", "MDT")));
    }
}
