package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DaidzaiServiceClientTest {

    private DaidzaiServiceClient client(RestTemplate rt) {
        return new DaidzaiServiceClient(rt, ServiceClientConfig.testServiceEndpoints());
    }

    /**
     * The PD-3 dispatch gate: triaging an anonymous request still awaiting callback
     * returns daidzai 409 CALLBACK_VERIFICATION_REQUIRED. The BFF proxy must SURFACE that
     * 409 (with the domain message), not collapse it into a 500 — the rig-caught defect.
     */
    @Test
    void triage_preservesDownstream409AndBody() {
        RestTemplate rt = mock(RestTemplate.class);
        String body = "{\"error\":{\"code\":\"CALLBACK_VERIFICATION_REQUIRED\",\"message\":\"callback verification required before dispatch\"}}";
        when(rt.postForEntity(any(String.class), any(), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                        body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client(rt).triageRequest("req-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains("CALLBACK_VERIFICATION_REQUIRED"));
    }

    @Test
    void getRequest_preservesDownstream404() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForEntity(any(String.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        new byte[0], StandardCharsets.UTF_8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client(rt).getRequest("missing"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
