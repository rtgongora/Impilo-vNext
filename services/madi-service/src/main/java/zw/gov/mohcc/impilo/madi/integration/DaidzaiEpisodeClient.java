package zw.gov.mohcc.impilo.madi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thin best-effort client to the DAIDZAI canonical trauma-episode spine. MADI owns its blood
 * system-of-record rows and carries the shared {@code trauma_episode_id}; when a trauma blood
 * order is placed it registers a BLOOD phase on the episode read-model timeline. Failure is
 * swallowed — a correlation side-channel must never roll back or block a blood transaction.
 *
 * <p>Service-originated call — synthesises the mandatory trust envelope.</p>
 */
@Service
public class DaidzaiEpisodeClient {

    private static final Logger log = LoggerFactory.getLogger(DaidzaiEpisodeClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DaidzaiEpisodeClient(RestTemplate restTemplate,
                                @Value("${madi.integration.daidzai.base-url:http://localhost:8392}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void registerPhase(UUID tenantId, UUID episodeId, String phase, String ownerRef,
                              String status, String eventType) {
        if (episodeId == null) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", phase);
        body.put("ownerService", "madi-service");
        body.put("ownerRef", ownerRef);
        body.put("status", status);
        body.put("eventType", eventType);
        try {
            restTemplate.postForEntity(baseUrl + "/internal/v1/daidzai/trauma-episodes/" + episodeId + "/phases",
                    new HttpEntity<>(body, headers(tenantId)), Void.class);
        } catch (RestClientException e) {
            log.warn("DAIDZAI phase registration ({}) for episode {} failed: {} — projection stays honest",
                    phase, episodeId, e.getMessage());
        }
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "madi-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        h.set("Idempotency-Key", "madi-ep-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
