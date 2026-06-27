package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsentControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private TshepoConsentServiceClient consentClient;
    @Mock private MvumoServiceClient mvumoClient;

    private PolicyConsentController controller() {
        return new PolicyConsentController(consentClient, mvumoClient);
    }

    @Test
    void acceptConsent_persistsBothPoliciesToMvumo() {
        ResponseEntity<Map<String, Object>> response =
                controller().acceptConsent("t1", "pod-1", "req-1", "corr-1",
                        "actor-1", null, null,
                        Map.of("userId", "user-1", "version", "1.0",
                                "privacyPolicyAccepted", true,
                                "termsOfUseAccepted", true,
                                "channel", "WEB"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("policy_consent", ((Map<?, ?>) response.getBody().get("data")).get("type"));
        // Both Privacy Policy and Terms persisted to Mvumo, bound to the authenticated actor.
        verify(mvumoClient, times(2)).acceptLegalAgreement(any());
    }

    @Test
    void acceptConsent_failsSafe_whenMvumoUnavailable() {
        when(mvumoClient.acceptLegalAgreement(any()))
                .thenThrow(new RuntimeException("mvumo down"));

        ResponseEntity<Map<String, Object>> response =
                controller().acceptConsent("t1", "pod-1", "req-1", "corr-1",
                        "actor-1", null, null,
                        Map.of("userId", "user-1", "version", "1.0",
                                "privacyPolicyAccepted", true, "termsOfUseAccepted", false, "channel", "WEB"));

        // Mvumo outage must NOT block the login consent interstitial.
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getConsentStatus_returnsGrantedAgreementsFromMvumo() throws Exception {
        when(mvumoClient.listLegalAgreements(eq("actor-1"))).thenReturn(MAPPER.readTree(
                "[{\"id\":\"a1\",\"documentType\":\"TERMS_OF_USE\",\"version\":\"1.0\",\"state\":\"GRANTED\"},"
                        + "{\"id\":\"a2\",\"documentType\":\"PRIVACY_POLICY\",\"version\":\"0.9\",\"state\":\"WITHDRAWN\"}]"));

        ResponseEntity<Map<String, Object>> response =
                controller().getConsentStatus("t1", "req-2", "corr-2", "actor-1", null);

        assertEquals(200, response.getStatusCode().value());
        List<?> data = (List<?>) response.getBody().get("data");
        assertEquals(1, data.size(), "status returns only GRANTED agreements");
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) data.get(0)).get("attributes");
        assertEquals("TERMS_OF_USE", attrs.get("policyType"));
    }

    @Test
    void getConsentStatus_requiresActorId() {
        ResponseEntity<Map<String, Object>> response =
                controller().getConsentStatus("t1", "req-2", "corr-2", null, null);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void revokeConsent_withdrawsMatchingGrantedAgreement() throws Exception {
        when(mvumoClient.listLegalAgreements(eq("actor-1"))).thenReturn(MAPPER.readTree(
                "[{\"id\":\"a1\",\"documentType\":\"PRIVACY_POLICY\",\"version\":\"1.0\",\"state\":\"GRANTED\"}]"));

        ResponseEntity<Map<String, Object>> response =
                controller().revokeConsent("t1", "pod-1", "req-3", "corr-3",
                        "actor-1", Map.of("policyType", "PRIVACY_POLICY"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("policy_consent_revocation", ((Map<?, ?>) response.getBody().get("data")).get("type"));
        verify(mvumoClient).withdrawLegalAgreement(eq("a1"));
    }
}
