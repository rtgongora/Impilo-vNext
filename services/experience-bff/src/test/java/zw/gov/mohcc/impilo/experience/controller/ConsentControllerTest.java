package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsentControllerTest {

    @Test
    void acceptConsent_returnsAcceptedPolicies() {
        PolicyConsentController controller = new PolicyConsentController(null);
        ResponseEntity<Map<String, Object>> response =
                controller.acceptConsent("t1", "pod-1", "req-1", "corr-1",
                        "actor-1", null, null,
                        Map.of("userId", "user-1", "version", "1.0",
                                "privacyPolicyAccepted", true,
                                "termsOfUseAccepted", true,
                                "channel", "WEB"));
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("policy_consent", data.get("type"));
    }

    @Test
    void getConsentStatus_requiresActorId() {
        PolicyConsentController controller = new PolicyConsentController(null);
        ResponseEntity<Map<String, Object>> response =
                controller.getConsentStatus("t1", "req-2", "corr-2", null, null);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void revokeConsent_returnsRevocationRecord() {
        PolicyConsentController controller = new PolicyConsentController(null);
        ResponseEntity<Map<String, Object>> response =
                controller.revokeConsent("t1", "pod-1", "req-3", "corr-3",
                        "actor-1",
                        Map.of("policyType", "PRIVACY_POLICY"));
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("policy_consent_revocation", data.get("type"));
    }
}
