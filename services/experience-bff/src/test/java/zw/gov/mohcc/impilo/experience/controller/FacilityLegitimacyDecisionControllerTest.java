package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FCV-W1 — the experience layer must not turn a refusal into an outage.
 *
 * <p>"You hold no appointment covering this facility" and "the registry is unreachable" are
 * different facts. Collapsing the first into a generic 502 would tell a verifier the system is
 * broken when in truth they were correctly refused.</p>
 */
class FacilityLegitimacyDecisionControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aRefusalReachesTheVerifierWithItsReason() {
        TusoServiceClient tuso = mock(TusoServiceClient.class);
        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY,
                ("{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Issuing a Ministry legitimacy verdict "
                        + "requires an active Ministry verifier appointment whose jurisdiction covers this "
                        + "facility.\"}}").getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(tuso.issueLegitimacyDecision(anyString(), any()))
                .thenThrow(new IllegalStateException("tuso POST failed", forbidden));

        ResponseEntity<Map<String, Object>> resp = new FacilityLegitimacyDecisionController(tuso)
                .issue("req", "corr", "fac-1", Map.of("verdict", "LEGITIMATE_OPERATIONAL"));

        assertEquals(403, resp.getStatusCode().value());
        assertEquals("FORBIDDEN", ((Map<?, ?>) resp.getBody().get("error")).get("code"));
    }

    @Test
    void anOutageIsReportedAsAnOutage() {
        TusoServiceClient tuso = mock(TusoServiceClient.class);
        when(tuso.issueLegitimacyDecision(anyString(), any()))
                .thenThrow(new IllegalStateException("connection refused"));

        ResponseEntity<Map<String, Object>> resp = new FacilityLegitimacyDecisionController(tuso)
                .issue("req", "corr", "fac-1", Map.of("verdict", "LEGITIMATE_OPERATIONAL"));

        assertEquals(502, resp.getStatusCode().value());
        assertEquals("TUSO_UNAVAILABLE", ((Map<?, ?>) resp.getBody().get("error")).get("code"));
    }

    @Test
    void aSuccessfulDecisionIsRelayedAsCreated() {
        TusoServiceClient tuso = mock(TusoServiceClient.class);
        when(tuso.issueLegitimacyDecision(anyString(), any()))
                .thenReturn(MAPPER.createObjectNode().put("platformAccessAllowed", true));

        ResponseEntity<Map<String, Object>> resp = new FacilityLegitimacyDecisionController(tuso)
                .issue("req", "corr", "fac-1", Map.of("verdict", "LEGITIMATE_OPERATIONAL"));

        assertEquals(201, resp.getStatusCode().value());
    }
}
