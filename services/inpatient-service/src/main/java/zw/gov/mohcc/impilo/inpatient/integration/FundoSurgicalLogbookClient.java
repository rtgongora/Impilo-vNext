package zw.gov.mohcc.impilo.inpatient.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Best-effort client posting a trainee surgical logbook / CPD completion to learning-service
 * (Fundo, {@code /internal/v1/learning/integrations/varapi-fundo-completion}). This feeds the canonical
 * CPD ledger (varapi) which governs privilege progression (Wave 4 §6c). FAIL-SAFE BY REFERENCE — a
 * learning-record failure NEVER blocks clinical case completion. Idempotent via externalRef
 * ("theatre-episode:&lt;episodeId&gt;").
 */
@Service
public class FundoSurgicalLogbookClient {

    private static final Logger log = LoggerFactory.getLogger(FundoSurgicalLogbookClient.class);
    static final String INTERNAL_KEY_HEADER = "X-Impilo-Learning-Internal-Key";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalKey;

    public FundoSurgicalLogbookClient(
            RestTemplate inpatientRestTemplate,
            @Value("${inpatient.integration.learning.base-url:http://localhost:8235}") String baseUrl,
            @Value("${inpatient.integration.learning.internal-key:}") String internalKey) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
        this.internalKey = internalKey;
    }

    /**
     * Post a surgical logbook / CPD completion for a trainee who was supervised on this case.
     *
     * @return true if learning-service accepted the record; false on any failure (best-effort).
     */
    public boolean postSurgicalLogbook(String tenantId, String traineeProviderPublicId, String procedureCode,
                                       String procedureName, int creditsSuggested, String episodeId) {
        if (traineeProviderPublicId == null || traineeProviderPublicId.isBlank()) return false;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("providerPublicId", traineeProviderPublicId);
            payload.put("courseId", "SURG-LOGBOOK-" + (procedureCode != null ? procedureCode : "CASE"));
            payload.put("courseName", "Surgical logbook — "
                    + (procedureName != null ? procedureName : "operative case"));
            payload.put("completedAt", Instant.now().toString());
            payload.put("creditsSuggested", creditsSuggested);
            payload.put("externalRef", "theatre-episode:" + episodeId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalKey != null && !internalKey.isBlank()) headers.set(INTERNAL_KEY_HEADER, internalKey);
            headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
            restTemplate.postForEntity(baseUrl + "/internal/v1/learning/integrations/varapi-fundo-completion",
                    new HttpEntity<>(payload, headers), String.class);
            log.info("INPATIENT-THEATRE: posted trainee surgical logbook/CPD for provider {} (episode {})",
                    traineeProviderPublicId, episodeId);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            // Fail-safe by reference: a learning-record failure must NEVER block clinical completion.
            log.warn("INPATIENT-THEATRE: Fundo surgical logbook post failed for provider {} (best-effort, non-blocking): {}",
                    traineeProviderPublicId, e.getMessage());
            return false;
        }
    }
}
