package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class AuthSessionControllerTest {

    @Test
    void logout_returnsLoggedOutStatus() {
        AuthSessionController controller = new AuthSessionController(new RestTemplate(), null, null, null);
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
        AuthSessionController controller = new AuthSessionController(new RestTemplate(), null, null, null);
        ResponseEntity<Map<String, Object>> response =
                controller.getSession("t1", "req-2", "corr-2", null);
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals(false, data.get("authenticated"));
        assertEquals("t1", data.get("tenant_id"));
    }

    @Test
    void getSession_returnsAuthenticatedTrueWithAuth() {
        AuthSessionController controller = new AuthSessionController(new RestTemplate(), null, null, null);
        ResponseEntity<Map<String, Object>> response =
                controller.getSession("t1", "req-3", "corr-3", "Bearer some-token");
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals(true, data.get("authenticated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildLoginResponse_refreshCookieUsesRefreshExpiresIn() throws Exception {
        AuthSessionController controller = new AuthSessionController(new RestTemplate(), null, null, null);

        Method method = AuthSessionController.class.getDeclaredMethod(
                "buildLoginResponse",
                String.class, String.class, int.class, int.class,
                String.class, String.class, String.class,
                List.class, String.class, String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);

        int accessTokenLifetime = 300;
        int refreshTokenLifetime = 1800;

        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) method.invoke(
                controller,
                "some-token", "some-refresh-token", accessTokenLifetime, refreshTokenLifetime,
                "user-1", "user@example.com", "User One",
                List.of("CITIZEN"), "CITIZEN", "user@example.com", "email",
                "req-1", "corr-1");

        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeaders, "Set-Cookie header must be present");
        String refreshCookie = setCookieHeaders.stream()
                .filter(h -> h.startsWith("exp_refresh_token="))
                .findFirst()
                .orElse(null);
        assertNotNull(refreshCookie, "exp_refresh_token cookie must be set");
        assertTrue(refreshCookie.contains("Max-Age=" + refreshTokenLifetime),
                "Cookie Max-Age should equal refresh_expires_in (" + refreshTokenLifetime + "), got: " + refreshCookie);
        assertFalse(refreshCookie.contains("Max-Age=" + accessTokenLifetime),
                "Cookie Max-Age must NOT equal expires_in (" + accessTokenLifetime + ")");
    }
}
