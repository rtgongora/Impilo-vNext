package zw.gov.mohcc.impilo.learning.api;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.learning.api.dto.VarapiFundoCompletionPayload;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;
import zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade;

/**
 * Trusted ingress from Varapi after Fundo webhook creates a {@code FundoCpdCandidate}. Uses a shared
 * internal key (not end-user JWT). Restrict at the network layer in production.
 */
@RestController
@RequestMapping("/internal/v1/learning/integrations")
public class VarapiLearningIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(VarapiLearningIntegrationController.class);

    public static final String INTERNAL_KEY_HEADER = "X-Impilo-Learning-Internal-Key";

    private final LearningPlatformFacade facade;
    private final LearningProperties learningProperties;

    public VarapiLearningIntegrationController(LearningPlatformFacade facade, LearningProperties learningProperties) {
        this.facade = facade;
        this.learningProperties = learningProperties;
    }

    @PostMapping("/varapi-fundo-completion")
    public ResponseEntity<?> ingest(
            @RequestHeader(value = INTERNAL_KEY_HEADER, required = false) String apiKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        if (!constantTimeEquals(
                configuredKeyBytes(), apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected learning integration: bad or missing internal key from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        try {
            VarapiFundoCompletionPayload p = mapPayload(body);
            Map<String, Object> result = facade.ingestVarapiFundoCompletion(p);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Invalid varapi-fundo-completion payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static VarapiFundoCompletionPayload mapPayload(Map<String, Object> body) {
        UUID tenantId = UUID.fromString(body.get("tenantId").toString());
        String providerPublicId = body.get("providerPublicId").toString();
        Long councilId = body.get("councilId") != null ? Long.valueOf(body.get("councilId").toString()) : null;
        String courseId = body.get("courseId").toString();
        String courseName = body.get("courseName") != null ? body.get("courseName").toString() : null;
        Instant completedAt = Instant.parse(body.get("completedAt").toString());
        int credits =
                body.get("creditsSuggested") != null ? Integer.parseInt(body.get("creditsSuggested").toString()) : 0;
        String externalRef = body.get("externalRef").toString();
        Long candidateId = Long.valueOf(body.get("varapiFundoCandidateId").toString());
        return new VarapiFundoCompletionPayload(
                tenantId, providerPublicId, councilId, courseId, courseName, completedAt, credits, externalRef, candidateId);
    }

    private byte[] configuredKeyBytes() {
        String k = learningProperties.getIntegration().getVarapiInternalKey();
        return k == null ? new byte[0] : k.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected.length == 0 || expected.length != actual.length) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }
}
