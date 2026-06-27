package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpaDecisionClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private RestTemplate restTemplate;
    private OpaDecisionClient client;

    @BeforeEach
    void setUp() {
        AuthzProperties props = new AuthzProperties();
        props.setOpaUrl("http://opa:8181");
        client = new OpaDecisionClient(restTemplate, props);
    }

    @Test
    void parsesDenyDecision() throws Exception {
        JsonNode node = MAPPER.readTree("{\"result\":{\"allow\":false,\"deny_reasons\":[\"MIN_LOA\"]}}");
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(node));

        OpaDecision d = client.decide(Map.of("actor_id", "a", "purpose", "TREATMENT", "loa", 1));

        assertFalse(d.allow());
        assertTrue(d.denyReasons().contains("MIN_LOA"));
    }

    @Test
    void parsesAllowDecision() throws Exception {
        JsonNode node = MAPPER.readTree("{\"result\":{\"allow\":true,\"deny_reasons\":[]}}");
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(node));

        assertTrue(client.decide(Map.of("actor_id", "a")).allow());
    }

    @Test
    void undefinedResult_returnsNull() throws Exception {
        JsonNode node = MAPPER.readTree("{}"); // no "result" (e.g. missing actor)
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(node));

        assertNull(client.decide(Map.of()));
    }

    @Test
    void transportError_returnsNull() {
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("opa down"));

        assertNull(client.decide(Map.of("actor_id", "a")));
    }
}
