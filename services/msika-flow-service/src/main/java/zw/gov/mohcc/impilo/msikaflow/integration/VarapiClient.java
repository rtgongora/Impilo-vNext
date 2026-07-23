package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * VARAPI professional-registry client — eligibility precondition 1 (§4.3),
 * provider-standing checks, and the V009 recognition convenience. Uses the
 * VashandiClient {@code copyTrustHeaders} idiom (via {@link TusoClient}): the
 * inbound request's trust headers are forwarded on every call so VARAPI's
 * TrustContext filter never sees a null tenant (live-caught BUG-1: a bare
 * RestTemplate sent no headers → registry read failed → the precondition
 * fail-closed UNVERIFIED for every vendor).
 *
 * <p>Graceful degradation is preserved: any failure returns {@code null} so
 * callers map the outage to UNVERIFIED / fail closed — never a throw.</p>
 */
@Service
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VarapiClient(ObjectMapper objectMapper,
                        @Value("${msika-flow.integration.varapi-url:http://localhost:8083}") String varapiBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(varapiBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /** Resolves the current request thread's inbound request for trust-header propagation. */
    public JsonNode getStandingSummary(String providerPublicId) {
        return getStandingSummary(providerPublicId, TusoClient.currentRequest());
    }

    public JsonNode getStandingSummary(String providerPublicId, HttpServletRequest inbound) {
        return getJson("/v1/internal/providers/" + providerPublicId + "/standing-summary",
                inbound, "standing-summary " + providerPublicId);
    }

    /**
     * Provider recognition by Health ID — {recognised, licenceStatus,
     * profession, cadre}. Upstream is enumeration-resistant (generic
     * recognised=false for unknown ids). Returns null on transport failure so
     * callers FAIL CLOSED for authorization decisions (a down registry never
     * self-authorizes a regulated purchase).
     */
    public JsonNode getRecognitionByHealthId(String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return null;
        }
        return getJson("/v1/internal/providers/by-health-id/" + healthId.trim() + "/recognition",
                TusoClient.currentRequest(), "recognition lookup");
    }

    private JsonNode getJson(String path, HttpServletRequest inbound, String what) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(path)
                    .headers(h -> TusoClient.copyTrustHeaders(inbound, h))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("VARAPI {} failed: HTTP {}", what, response.getStatusCode());
                return null;
            }
            return objectMapper.readTree(response.getBody()).path("data");
        } catch (Exception e) {
            log.warn("VARAPI {} failed: {}", what, e.getMessage());
            return null;
        }
    }
}
