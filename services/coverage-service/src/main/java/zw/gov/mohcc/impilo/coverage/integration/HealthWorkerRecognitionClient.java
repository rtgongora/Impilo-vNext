package zw.gov.mohcc.impilo.coverage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Dual-source health-worker recognition check for subsidy governance:
 * VARAPI (licensed providers) + Vashandi (employed workforce), queried
 * directly (coverage is a sovereign service; no BFF hop).
 *
 * <p>FAIL-CLOSED FOR GRANTS: any transport error yields "not recognised" —
 * an unavailable registry can never mint a subsidy enrolment. The same
 * negative answer drives the suspension sweep, which is deliberately
 * conservative there too (see {@code HealthWorkerSubsidyService}: a sweep
 * only suspends on a definite negative from a REACHABLE source pair).</p>
 */
@Component
public class HealthWorkerRecognitionClient {

    private static final Logger log = LoggerFactory.getLogger(HealthWorkerRecognitionClient.class);

    /**
     * Recognition verdict. {@code sourcesReachable} distinguishes a definite
     * "not recognised" from "could not verify" so lifecycle sweeps do not
     * suspend members during a registry outage.
     */
    public record Recognition(boolean recognised, String recognitionClass, boolean sourcesReachable) {
        public static Recognition unreachable() {
            return new Recognition(false, null, false);
        }

        public static Recognition negative() {
            return new Recognition(false, null, true);
        }
    }

    private final RestClient varapiRestClient;
    private final RestClient vashandiRestClient;
    private final ObjectMapper objectMapper;

    public HealthWorkerRecognitionClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.varapi-base-url:http://localhost:8083}") String varapiBaseUrl,
            @Value("${impilo.services.vashandi-base-url:http://localhost:8167}") String vashandiBaseUrl,
            ObjectMapper objectMapper) {
        this.varapiRestClient = restClientBuilder.baseUrl(varapiBaseUrl.replaceAll("/$", "")).build();
        this.vashandiRestClient = restClientBuilder.clone().baseUrl(vashandiBaseUrl.replaceAll("/$", "")).build();
        this.objectMapper = objectMapper;
    }

    public Recognition resolve(UUID tenantId, String healthId) {
        if (tenantId == null || healthId == null || healthId.isBlank()) {
            return Recognition.negative();
        }
        String id = healthId.trim();
        JsonNode provider = fetch(varapiRestClient,
                "/v1/internal/providers/by-health-id/" + id + "/recognition", tenantId, true);
        JsonNode workforce = fetch(vashandiRestClient,
                "/v1/internal/vashandi/workforce/by-health-id/" + id + "/recognition", tenantId, false);

        if (provider == null && workforce == null) {
            return Recognition.unreachable();
        }
        boolean p = provider != null && provider.path("recognised").asBoolean(false);
        boolean w = workforce != null && workforce.path("recognised").asBoolean(false);
        if (!p && !w) {
            // Only a definite negative when BOTH sources answered.
            return (provider != null && workforce != null)
                    ? Recognition.negative()
                    : Recognition.unreachable();
        }
        String cls = p && w ? "BOTH" : p ? "LICENSED_PROVIDER" : "HEALTH_WORKFORCE";
        return new Recognition(true, cls, true);
    }

    /** null on ANY failure. VARAPI wraps in ApiResponse {data:{...}}. */
    private JsonNode fetch(RestClient client, String path, UUID tenantId, boolean unwrapData) {
        try {
            var response = client.get()
                    .uri(path)
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Pod-ID", "national-spine")
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .header("X-Actor-ID", "coverage-service")
                    .header("X-Actor-Type", "SYSTEM")
                    .header("X-Purpose-Of-Use", "COVERAGE")
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode body = objectMapper.readTree(response.getBody());
            return unwrapData && body.has("data") ? body.get("data") : body;
        } catch (Exception e) {
            log.debug("Recognition source unavailable ({}): {}", path, e.getMessage());
            return null;
        }
    }
}
