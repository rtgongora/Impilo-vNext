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
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.service.PublicFeedbackIntakeService;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the anonymous public feedback intake lane (gateway ADR W4,
 * gateway-feedback-claim): allow-listed case types, claim-code receipt, FAIL-CLOSED rate
 * limiting (unlike the fail-open SOS lane), and public-naming safety.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicGatewayFeedbackBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private KeycloakAdminClient keycloakAdminClient;

    private StubRitoClient rito;
    private PublicGatewayFeedbackBffController controller;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redis.getExpire(anyString())).thenReturn(300L);
        when(keycloakAdminClient.serviceAccountBearer()).thenReturn(null);

        rito = new StubRitoClient();
        PublicFeedbackIntakeService service =
                new PublicFeedbackIntakeService(redis, rito, keycloakAdminClient);
        controller = new PublicGatewayFeedbackBffController(service);
    }

    @Test
    void happyPath_returns201WithClaimCodeAndReference() {
        Map<String, Object> body = new HashMap<>();
        body.put("caseType", "SAFETY_CONCERN");
        body.put("title", "Unsafe ward");
        body.put("description", "Equipment left in the corridor");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().get("claimCode")).isEqualTo("TESTCLAIMCODE2345678");
        assertThat(response.getBody().get("caseReference")).isEqualTo("RITO-C-2026-TEST01");
        // Public naming safety: no internal service names in the citizen-facing message.
        assertThat(String.valueOf(response.getBody().get("message"))).doesNotContain("rito", "Rito");
        assertThat(rito.lastBody.get("caseType")).isEqualTo("SAFETY_CONCERN");
    }

    @Test
    void nonAllowlistedCaseType_is400AndNeverReachesDownstream() {
        Map<String, Object> body = new HashMap<>();
        body.put("caseType", "ADVERSE_EVENT");
        body.put("title", "t");
        body.put("description", "d");

        assertThat(controller.submit(body, request("41.1.2.3")).getStatusCode().value()).isEqualTo(400);
        assertThat(rito.calls).isZero();
    }

    @Test
    void missingTitleOrDescription_is400() {
        Map<String, Object> body = new HashMap<>();
        body.put("caseType", "COMPLAINT");
        body.put("title", "only a title");

        assertThat(controller.submit(body, request("41.1.2.3")).getStatusCode().value()).isEqualTo(400);
        assertThat(rito.calls).isZero();
    }

    @Test
    void rateLimitBreach_is429WithRetryAfter() {
        when(valueOps.increment(anyString()))
                .thenReturn((long) PublicFeedbackIntakeService.MAX_REQUESTS_PER_IP + 1);

        Map<String, Object> body = new HashMap<>();
        body.put("caseType", "COMPLAINT");
        body.put("title", "Long wait");
        body.put("description", "Waited five hours");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
        assertThat(rito.calls).isZero();
    }

    @Test
    void redisDown_failsClosed_unlikeSosLane() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        Map<String, Object> body = new HashMap<>();
        body.put("caseType", "COMPLAINT");
        body.put("title", "Long wait");
        body.put("description", "Waited five hours");

        ResponseEntity<Map<String, Object>> response = controller.submit(body, request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(rito.calls).isZero();
    }

    @Test
    void statusLookup_returnsDisclosureLimitedView() {
        ResponseEntity<Map<String, Object>> response =
                controller.status("TESTCLAIMCODE2345678", request("41.1.2.3"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("caseReference")).isEqualTo("RITO-C-2026-TEST01");
        assertThat(response.getBody().get("status")).isEqualTo("SUBMITTED");
        assertThat(response.getBody()).doesNotContainKeys("description", "title", "reporterActorId");
    }

    @Test
    void malformedClaimCode_is400WithoutDownstreamCall() {
        assertThat(controller.status("not a code!!", request("41.1.2.3"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(rito.statusCalls).isZero();
    }

    private static HttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private static final class StubRitoClient extends RitoServiceClient {
        Map<String, Object> lastBody = new HashMap<>();
        int calls;
        int statusCalls;

        StubRitoClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode createPublicCase(Map<String, Object> body, String serviceAccountBearer) {
            calls++;
            lastBody = new HashMap<>(body);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("caseReference", "RITO-C-2026-TEST01");
            node.put("claimCode", "TESTCLAIMCODE2345678");
            node.put("status", "SUBMITTED");
            return node;
        }

        @Override
        public JsonNode getPublicCaseStatus(String claimCode, String serviceAccountBearer) {
            statusCalls++;
            ObjectNode node = MAPPER.createObjectNode();
            node.put("caseReference", "RITO-C-2026-TEST01");
            node.put("caseType", "SAFETY_CONCERN");
            node.put("status", "SUBMITTED");
            node.put("createdAt", "2026-07-14T00:00:00Z");
            node.put("updatedAt", "2026-07-14T00:00:00Z");
            return node;
        }
    }
}
