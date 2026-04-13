package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthSessionControllerTest {

    @Test
    void logout_returnsLoggedOutStatus() {
        AuthSessionController controller = new AuthSessionController(new RestTemplate());
        ResponseEntity<Map<String, Object>> response =
                controller.logout("t1", "req-1", "corr-1", null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("logged_out", ((Map<?, ?>) data.get("attributes")).get("status"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void getSession_returnsAuthenticatedFalseWithoutAuth() {
        AuthSessionController controller = new AuthSessionController(new RestTemplate());
        ResponseEntity<Map<String, Object>> response =
                controller.getSession("t1", "req-2", "corr-2", null);
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals(false, data.get("authenticated"));
        assertEquals("t1", data.get("tenant_id"));
    }

    @Test
    void getSession_returnsAuthenticatedTrueWithAuth() {
        AuthSessionController controller = new AuthSessionController(new RestTemplate());
        ResponseEntity<Map<String, Object>> response =
                controller.getSession("t1", "req-3", "corr-3", "Bearer some-token");
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals(true, data.get("authenticated"));
    }
}
