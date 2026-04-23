package zw.gov.mohcc.impilo.varapi.api.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.core.FundoCpdGovernanceService;
import zw.gov.mohcc.impilo.varapi.integration.LearningPlatformSyncClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoCpdCandidateEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Ingests Moodle / Impilo Fundo completion webhooks (HMAC-protected).
 * Produces {@link FundoCpdCandidateEntity} rows — not final CPD credit until council acceptance rules run.
 */
@RestController
@RequestMapping("/v1/webhooks/fundo")
public class FundoWebhookController {

    public static final String H_SIGNATURE = "X-Fundo-Signature";

    private final VarapiProperties varapiProperties;
    private final FundoCpdGovernanceService fundoCpdGovernanceService;
    private final LearningPlatformSyncClient learningPlatformSyncClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FundoWebhookController(
            VarapiProperties varapiProperties,
            FundoCpdGovernanceService fundoCpdGovernanceService,
            LearningPlatformSyncClient learningPlatformSyncClient) {
        this.varapiProperties = varapiProperties;
        this.fundoCpdGovernanceService = fundoCpdGovernanceService;
        this.learningPlatformSyncClient = learningPlatformSyncClient;
    }

    @PostMapping("/cpd-completion")
    public ResponseEntity<String> cpdCompletion(
            @RequestHeader(H_SIGNATURE) String signature,
            @RequestBody String rawBody) {
        if (!varapiProperties.getFundo().isWebhookEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("disabled");
        }
        String secret = varapiProperties.getFundo().getWebhookSharedSecret();
        if (secret == null || secret.isBlank() || "CHANGE_ME".equals(secret)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("secret not configured");
        }
        String expected = FundoSignatureUtil.hmacSha256Hex(secret, rawBody.getBytes(StandardCharsets.UTF_8));
        if (!FundoSignatureUtil.constantTimeEquals(expected.toLowerCase(), signature.toLowerCase().trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
        }
        try {
            JsonNode n = objectMapper.readTree(rawBody);
            UUID tenantId = UUID.fromString(n.get("tenantId").asText());
            String providerPublicId = n.get("providerPublicId").asText();
            Long councilId = n.hasNonNull("councilId") ? n.get("councilId").asLong() : null;
            String courseId = n.get("courseId").asText();
            String courseName = n.hasNonNull("courseName") ? n.get("courseName").asText() : null;
            Instant completedAt = Instant.parse(n.get("completedAt").asText());
            int credits = n.hasNonNull("creditsSuggested") ? n.get("creditsSuggested").asInt(0) : 0;
            String externalRef = n.get("externalRef").asText();

            FundoCpdCandidateEntity saved = fundoCpdGovernanceService.ingestCompletion(
                    tenantId,
                    providerPublicId,
                    councilId,
                    courseId,
                    courseName,
                    completedAt,
                    credits,
                    externalRef,
                    rawBody);
            learningPlatformSyncClient.notifyAfterFundoIngest(n, saved);
            return ResponseEntity.ok("{\"candidateId\":" + saved.getId() + "}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
