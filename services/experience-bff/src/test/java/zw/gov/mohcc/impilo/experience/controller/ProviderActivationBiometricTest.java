package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Provider biometric step-up (L2) — BFF normalisation contract.
 *
 * <p>The BFF collapses VARAPI's verify outcome into a single, always-200 signal:
 * MATCH proceeds, NO_MATCH blocks, everything else (outage / no enrolment) is
 * UNAVAILABLE so activation falls back to the existing factor and never blocks
 * on a biometric outage.</p>
 */
class ProviderActivationBiometricTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Stub VARAPI client that returns a canned verify body, or throws to simulate an outage. */
    static final class StubVarapi extends VarapiServiceClient {
        private final JsonNode body;
        private final RuntimeException toThrow;

        StubVarapi(JsonNode body, RuntimeException toThrow) {
            super(new RestTemplate(), ServiceClientConfig.testEndpointsStandardWireMocks());
            this.body = body;
            this.toThrow = toThrow;
        }

        @Override
        public JsonNode verifyProviderBiometric(String providerPublicId, Map<String, Object> requestBody) {
            if (toThrow != null) throw toThrow;
            return body;
        }
    }

    private Map<String, Object> probeBody() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("modality", "FINGERPRINT");
        b.put("probeBase64", "QUJD");
        return b;
    }

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void match_isSurfacedSoActivationCanProceed() {
        ProviderActivationController controller = new ProviderActivationController(
                new StubVarapi(json("{\"result\":\"MATCH\",\"confidence\":0.94,\"verificationEventId\":7}"), null));

        ResponseEntity<Map<String, Object>> res =
                controller.biometricVerify("t1", "req-1", "corr-1", "VAR-2026-001", probeBody());

        assertEquals(200, res.getStatusCode().value());
        assertEquals("MATCH", res.getBody().get("result"));
        assertEquals(7L, res.getBody().get("verificationEventId"));
    }

    @Test
    void noMatch_isSurfacedHonestlySoActivationBlocks() {
        ProviderActivationController controller = new ProviderActivationController(
                new StubVarapi(json("{\"result\":\"NO_MATCH\",\"confidence\":0.11}"), null));

        ResponseEntity<Map<String, Object>> res =
                controller.biometricVerify("t1", "req-2", "corr-2", "VAR-2026-001", probeBody());

        assertEquals(200, res.getStatusCode().value());
        assertEquals("NO_MATCH", res.getBody().get("result"));
    }

    @Test
    void upstreamOutcomeUnavailable_collapsesToUnavailable() {
        ProviderActivationController controller = new ProviderActivationController(
                new StubVarapi(json("{\"result\":\"UNAVAILABLE\",\"confidence\":0.0}"), null));

        ResponseEntity<Map<String, Object>> res =
                controller.biometricVerify("t1", "req-3", "corr-3", "VAR-2026-001", probeBody());

        assertEquals(200, res.getStatusCode().value());
        assertEquals("UNAVAILABLE", res.getBody().get("result"));
    }

    @Test
    void transportOutage_neverBlocks_fallsBackToUnavailable() {
        ProviderActivationController controller = new ProviderActivationController(
                new StubVarapi(null, new RuntimeException("connection refused")));

        ResponseEntity<Map<String, Object>> res =
                controller.biometricVerify("t1", "req-4", "corr-4", "VAR-2026-001", probeBody());

        assertEquals(200, res.getStatusCode().value());
        assertEquals("UNAVAILABLE", res.getBody().get("result"));
        assertEquals("BIOMETRIC_SERVICE_UNAVAILABLE", res.getBody().get("reason"));
    }

    @Test
    void varapiErrorWithNoMatchBody_isSurfacedHonestlyNotFailedOpen() {
        HttpServerErrorException boom = HttpServerErrorException.create(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "err",
                org.springframework.http.HttpHeaders.EMPTY,
                "{\"data\":{\"result\":\"NO_MATCH\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8);
        ProviderActivationController controller = new ProviderActivationController(new StubVarapi(null, boom));

        ResponseEntity<Map<String, Object>> res =
                controller.biometricVerify("t1", "req-5", "corr-5", "VAR-2026-001", probeBody());

        assertEquals(200, res.getStatusCode().value());
        assertEquals("NO_MATCH", res.getBody().get("result"));
    }
}
