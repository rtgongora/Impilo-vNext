package zw.gov.mohcc.impilo.tshepo.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.config.TshepoLearningRelayProperties;

/**
 * Relays council-regulatory learning effects to {@code learning-service} after Tshepo policy
 * evaluation (or as a governed side-effect hop).
 */
@Component
public class LearningPlatformOrchestrationRelayClient {

    public static final String ORCHESTRATION_KEY_HEADER = "X-Impilo-Learning-Orchestration-Key";

    private static final Logger log = LoggerFactory.getLogger(LearningPlatformOrchestrationRelayClient.class);

    private final RestTemplate restTemplate;
    private final TshepoLearningRelayProperties props;

    public LearningPlatformOrchestrationRelayClient(RestTemplate restTemplate, TshepoLearningRelayProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    public ResponseEntity<String> postAssignPath(Map<String, Object> body) {
        return post("/internal/v1/learning/orchestration/assign-path", body);
    }

    public ResponseEntity<String> postRegisterPrerequisite(Map<String, Object> body) {
        return post("/internal/v1/learning/orchestration/register-prerequisite", body);
    }

    private ResponseEntity<String> post(String path, Map<String, Object> body) {
        if (!props.isRelayConfigured()) {
            return ResponseEntity.status(503).body("{\"error\":\"learning_relay_not_configured\"}");
        }
        String url = trim(props.getBaseUrl()) + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(ORCHESTRATION_KEY_HEADER, props.getPlatformOrchestrationKey());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            return restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            log.warn("learning relay POST {} failed: {}", path, e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"learning_unreachable\"}");
        }
    }

    public boolean relayMatches(String presentedKey) {
        if (!props.isRelayConfigured()) {
            return false;
        }
        byte[] expected = props.getRelayApiKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = presentedKey == null ? new byte[0] : presentedKey.getBytes(StandardCharsets.UTF_8);
        if (expected.length != actual.length) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private static String trim(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
