package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

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
    void login_rejectsBlankCredentials() {
        AuthSessionController controller = new AuthSessionController(new RestTemplate(), null, null, null);
        ResponseEntity<Map<String, Object>> response = controller.login(
                "t1", "pod-1", "req-login", "corr-login", null,
                Map.of("email", "", "password", ""));

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("VALIDATION", error.get("code"));
    }

    @Test
    void getLinkedIds_returnsProviderAndStaffFromUpstream() {
        AuthSessionController controller = new AuthSessionController(
                new RestTemplate(),
                new LinkedIdsVarapiClient(),
                new LinkedIdsVitoClient(),
                null);

        ResponseEntity<Map<String, Object>> response =
                controller.getLinkedIds("req-linked", "corr-linked", "actor-health-1");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        Map<?, ?> attrs = (Map<?, ?>) data.get("attributes");
        assertEquals("PRV-LINK-1", attrs.get("providerId"));
        assertEquals("STAFF-9", attrs.get("staffId"));
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

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class LinkedIdsVarapiClient extends VarapiServiceClient {
        LinkedIdsVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getProviderByHealthId(String healthId) {
            try {
                return new ObjectMapper().readTree("""
                        {"providerId":"PRV-LINK-1","status":"ACTIVE","licenceValid":true}
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class LinkedIdsVitoClient extends VitoServiceClient {
        LinkedIdsVitoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getStaffAssignments(String healthId) {
            try {
                return new ObjectMapper().readTree("""
                        {"staffId":"STAFF-9","facilityId":"FAC-001"}
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
