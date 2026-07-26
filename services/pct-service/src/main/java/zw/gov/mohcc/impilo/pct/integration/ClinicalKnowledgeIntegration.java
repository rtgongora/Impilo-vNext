package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thin client to the clinical knowledge platform's IMAM engine.
 *
 * <p>The boundary is the same one the growth and immunisation engines draw: PCT owns the episode
 * and its reviews, the knowledge platform owns the protocol. Sending the reviews out and getting a
 * verdict back keeps a single system of record for the treatment while leaving admission routing,
 * discharge criteria and the defaulter definition in content a clinician can revise without a
 * deployment.</p>
 *
 * <p><strong>Degradation is explicit, never silent.</strong> Every method returns {@code null} when
 * the platform is unreachable, and callers stamp a null assessment with the reason. A visit is
 * always recorded: losing the review is worse than losing its interpretation, and an unavailable
 * verdict must never be stored as "not ready for discharge", which would look like a clinical
 * finding about the child rather than a fact about the network.</p>
 *
 * <p>Service-originated call, so it synthesises the mandatory trust envelope.</p>
 */
@Service
public class ClinicalKnowledgeIntegration {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKnowledgeIntegration.class);

    private final RestTemplate restTemplate;
    private final ServiceTokenProvider tokenProvider;
    private final String baseUrl;

    public ClinicalKnowledgeIntegration(
            RestTemplate restTemplate,
            ServiceTokenProvider tokenProvider,
            @Value("${pct.integration.clinical-knowledge.base-url:${CLINICAL_KNOWLEDGE_PLATFORM_BASE_URL:http://localhost:8270}}")
            String baseUrl) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl;
    }

    /** @return the eligibility assessment, or {@code null} when the platform is unreachable */
    public Map<String, Object> imamEligibility(UUID tenantId, Map<String, Object> facts) {
        return post(tenantId, "/internal/v1/clinical/paediatric/imam/eligibility", facts, "IMAM eligibility");
    }

    /** @return the progress assessment, or {@code null} when the platform is unreachable */
    public Map<String, Object> imamProgress(UUID tenantId, Map<String, Object> body) {
        return post(tenantId, "/internal/v1/clinical/paediatric/imam/progress", body, "IMAM progress");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(UUID tenantId, String path, Map<String, Object> body, String what) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(baseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(body, headers(tenantId)), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("{} returned {} with no body — the assessment is recorded as unavailable",
                        what, response.getStatusCode());
                return null;
            }
            Object data = response.getBody().get("data");
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return (Map<String, Object>) response.getBody();
        } catch (RestClientException e) {
            log.warn("Clinical knowledge platform unreachable for {}: {} — the clinical write proceeds and "
                     + "the assessment is recorded as unavailable rather than as a negative finding",
                    what, e.getMessage());
            return null;
        }
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) {
            h.set("X-Tenant-ID", tenantId.toString());
        }
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "pct-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        // The knowledge platform is an OAuth2 resource server and authenticates every caller,
        // services included. Without this the call is a 401 that surfaces to the clinician as
        // "the protocol is unavailable" — technically honest, entirely useless.
        tokenProvider.accessToken().ifPresent(h::setBearerAuth);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Convenience for callers assembling the eligibility body. */
    public static Map<String, Object> eligibilityFacts(Integer ageDays, Object muacCm, String oedema,
                                                       String appetiteTest, Object weightForHeightZ,
                                                       Boolean medicalComplication, String classification) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("ageDays", ageDays);
        facts.put("muacCm", muacCm);
        facts.put("bilateralPittingOedema", oedema);
        facts.put("appetiteTest", appetiteTest);
        facts.put("weightForHeightZ", weightForHeightZ);
        facts.put("medicalComplication", medicalComplication);
        facts.put("classification", classification);
        return facts;
    }
}
