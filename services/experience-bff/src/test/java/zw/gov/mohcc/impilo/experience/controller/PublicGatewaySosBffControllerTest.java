package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService;
import zw.gov.mohcc.impilo.experience.service.PublicSosIntakeService;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the anonymous public SOS intake lane (gateway ADR §5, PD-3): required-callback
 * validation, rate limiting, the happy path, and public-naming safety (no internal service names).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicGatewaySosBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private KeycloakAdminClient keycloakAdminClient;

    private StubDaidzaiClient daidzai;
    private PublicGatewaySosBffController controller;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redis.getExpire(anyString())).thenReturn(300L);
        when(keycloakAdminClient.serviceAccountBearer()).thenReturn(null);

        daidzai = new StubDaidzaiClient();
        ContactOtpService otp = new ContactOtpService(redis, null, MAPPER, "+263");
        PublicSosIntakeService service = new PublicSosIntakeService(redis, daidzai, otp, keycloakAdminClient);
        controller = new PublicGatewaySosBffController(service);
    }

    @Test
    void happyPath_capturesRequestAndReturns202WithReference() {
        Map<String, Object> body = new HashMap<>();
        body.put("callbackNumber", "0771 234 567");
        body.put("emergencyCategory", "MEDICAL");
        body.put("severity", "HIGH");
        body.put("description", "Man collapsed at the market");
        body.put("locationDescription", "Mbare market");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsKey("requestReference");
        assertThat(response.getBody().get("requestReference")).isEqualTo("SOS-TEST-1");
        // Daidzai receives an anonymous public request with a normalized E.164 callback.
        assertThat(daidzai.lastBody.get("requesterType")).isEqualTo("PUBLIC_ANONYMOUS");
        assertThat(daidzai.lastBody.get("subjectIdentityMode")).isEqualTo("ANONYMOUS");
        assertThat(daidzai.lastBody.get("channel")).isEqualTo("PUBLIC_WEB");
        assertThat(daidzai.lastBody.get("callbackNumber")).isEqualTo("+263771234567");
    }

    @Test
    void missingCallback_is400AndNeverCallsDaidzai() {
        Map<String, Object> body = new HashMap<>();
        body.put("emergencyCategory", "MEDICAL");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(daidzai.calls).isZero();
    }

    @Test
    void invalidCallback_is400() {
        Map<String, Object> body = new HashMap<>();
        body.put("callbackNumber", "not-a-number");
        body.put("emergencyCategory", "MEDICAL");

        assertThat(controller.submit(body, request("41.1.2.3")).getStatusCode().value()).isEqualTo(400);
        assertThat(daidzai.calls).isZero();
    }

    @Test
    void rateLimitBreach_is429WithRetryAfter() {
        // Per-IP window breached on this request.
        when(valueOps.increment(anyString())).thenReturn((long) PublicSosIntakeService.MAX_REQUESTS_PER_IP + 1);

        Map<String, Object> body = new HashMap<>();
        body.put("callbackNumber", "+263771234567");
        body.put("emergencyCategory", "MEDICAL");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
        assertThat(daidzai.calls).isZero();
    }

    @Test
    void response_carriesNoInternalServiceNames() {
        Map<String, Object> body = new HashMap<>();
        body.put("callbackNumber", "+263771234567");
        body.put("emergencyCategory", "TRAUMA");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));
        String rendered = response.getBody().toString().toLowerCase();
        assertThat(rendered).doesNotContain("daidzai").doesNotContain("keycloak").doesNotContain("localhost");
    }

    private static HttpServletRequest request(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        return req;
    }

    /** Stub daidzai client capturing the outbound SOS body and returning a canned reference. */
    private static final class StubDaidzaiClient extends DaidzaiServiceClient {
        Map<String, Object> lastBody = new HashMap<>();
        int calls;

        StubDaidzaiClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode createPublicSosRequest(Map<String, Object> body, String serviceAccountBearer) {
            calls++;
            lastBody = new HashMap<>(body);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("requestReference", "SOS-TEST-1");
            node.put("status", "AWAITING_CALLBACK");
            node.put("callbackVerified", false);
            return node;
        }
    }
}
