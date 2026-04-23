package zw.gov.mohcc.impilo.tshepo.api.council;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.integration.LearningPlatformOrchestrationRelayClient;

/**
 * Internal relay: after council regulatory permit, upstream can ask Tshepo to forward learning
 * orchestration to {@code learning-service}. Secured with {@link LearningPlatformOrchestrationRelayClient}
 * relay API key.
 */
@RestController
@RequestMapping("/internal/v1/council-regulatory/learning")
public class CouncilLearningOrchestrationRelayController {

    public static final String RELAY_KEY_HEADER = "X-Tshepo-Learning-Relay-Key";

    private static final Logger log = LoggerFactory.getLogger(CouncilLearningOrchestrationRelayController.class);

    private final LearningPlatformOrchestrationRelayClient relayClient;

    public CouncilLearningOrchestrationRelayController(LearningPlatformOrchestrationRelayClient relayClient) {
        this.relayClient = relayClient;
    }

    @PostMapping("/assign-path")
    public ResponseEntity<String> assignPath(
            @RequestHeader(value = RELAY_KEY_HEADER, required = false) String relayKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (!relayClient.relayMatches(relayKey)) {
            log.warn("Rejected learning relay assign-path from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"unauthorized\"}");
        }
        return relayClient.postAssignPath(body);
    }

    @PostMapping("/register-prerequisite")
    public ResponseEntity<String> registerPrerequisite(
            @RequestHeader(value = RELAY_KEY_HEADER, required = false) String relayKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (!relayClient.relayMatches(relayKey)) {
            log.warn("Rejected learning relay register-prerequisite from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"unauthorized\"}");
        }
        return relayClient.postRegisterPrerequisite(body);
    }
}
