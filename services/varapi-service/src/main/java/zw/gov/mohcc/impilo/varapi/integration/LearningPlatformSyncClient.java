package zw.gov.mohcc.impilo.varapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoCpdCandidateEntity;

/**
 * Forwards governed Fundo completion facts to learning-service for platform-wide progress/completion
 * and audit (separate from CPD credit acceptance in Varapi).
 */
@Component
public class LearningPlatformSyncClient {

    private static final Logger log = LoggerFactory.getLogger(LearningPlatformSyncClient.class);

    public static final String INTERNAL_KEY_HEADER = "X-Impilo-Learning-Internal-Key";

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public LearningPlatformSyncClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    public void notifyAfterFundoIngest(JsonNode parsedBody, FundoCpdCandidateEntity saved) {
        VarapiProperties.LearningPlatformSyncProperties cfg = varapiProperties.getLearningPlatformSync();
        String base = cfg.getBaseUrl();
        String key = cfg.getInternalApiKey();
        if (base == null || base.isBlank() || key == null || key.isBlank()) {
            return;
        }
        String url = trimSlash(base) + "/internal/v1/learning/integrations/varapi-fundo-completion";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", parsedBody.get("tenantId").asText());
        body.put("providerPublicId", parsedBody.get("providerPublicId").asText());
        if (parsedBody.hasNonNull("councilId")) {
            body.put("councilId", parsedBody.get("councilId").asLong());
        } else {
            body.put("councilId", null);
        }
        body.put("courseId", parsedBody.get("courseId").asText());
        body.put("courseName", parsedBody.hasNonNull("courseName") ? parsedBody.get("courseName").asText() : null);
        body.put("completedAt", parsedBody.get("completedAt").asText());
        body.put(
                "creditsSuggested",
                parsedBody.hasNonNull("creditsSuggested") ? parsedBody.get("creditsSuggested").asInt(0) : 0);
        body.put("externalRef", parsedBody.get("externalRef").asText());
        body.put("varapiFundoCandidateId", saved.getId());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(INTERNAL_KEY_HEADER, key);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            log.warn("learning-platform-sync: POST failed — {}", e.getMessage());
        }
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
